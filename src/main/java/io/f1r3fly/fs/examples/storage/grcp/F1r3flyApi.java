package io.f1r3fly.fs.examples.storage.grcp;

import casper.CasperMessage;
import casper.DeployServiceCommon;
import casper.ProposeServiceCommon;
import casper.v1.DeployServiceGrpc;
import casper.v1.ProposeServiceGrpc;
import com.google.protobuf.ByteString;
import com.google.protobuf.ProtocolStringList;
import com.rfksystems.blake2b.Blake2b;
import com.rfksystems.blake2b.security.Blake2bProvider;
import io.f1r3fly.fs.FuseException;
import fr.acinq.secp256k1.Hex;
import fr.acinq.secp256k1.Secp256k1;
import io.f1r3fly.fs.examples.storage.errors.F1r3flyDeployError;
import io.f1r3fly.fs.examples.storage.errors.NoDataByPath;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.smallrye.mutiny.Uni;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rhoapi.RhoTypes;
import servicemodelapi.ServiceErrorOuterClass;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

public class F1r3flyApi {
    private static final Logger LOGGER = LoggerFactory.getLogger(F1r3flyApi.class);

    private static final Duration INIT_DELAY = Duration.ofMillis(100);
    private static final Duration MAX_DELAY = Duration.ofSeconds(5);
    private static final int RETRIES = 10;
    private static final int MAX_MESSAGE_SIZE = Integer.MAX_VALUE; // ~2 GB

    private final byte[] signingKey;
    private final DeployServiceGrpc.DeployServiceFutureStub deployService;
    private final ProposeServiceGrpc.ProposeServiceFutureStub proposeService;


    public F1r3flyApi(byte[] signingKey,
                      String nodeHost,
                      int grpcPort
    ) {
        super();


        ManagedChannel channel = ManagedChannelBuilder.forAddress(nodeHost, grpcPort).usePlaintext().build();

        this.signingKey = signingKey;
        this.deployService = DeployServiceGrpc.newFutureStub(channel)
            .withMaxInboundMessageSize(MAX_MESSAGE_SIZE)
            .withMaxOutboundMessageSize(MAX_MESSAGE_SIZE);
        this.proposeService = ProposeServiceGrpc.newFutureStub(channel)
            .withMaxInboundMessageSize(MAX_MESSAGE_SIZE)
            .withMaxOutboundMessageSize(MAX_MESSAGE_SIZE);
    }

    // Cut down on verbosity of surfacing successes
    private <T> Uni<T> succeed(T t) {
        return Uni.createFrom().item(t);
    }

    // Cut down on verbosity of surfacing errors
    private <T> Uni<T> fail(String rho, ServiceErrorOuterClass.ServiceError error) {
        return Uni.createFrom().failure(new F1r3flyDeployError(rho, gatherErrors(error)));
    }

    private String gatherErrors(ServiceErrorOuterClass.ServiceError error) {
        ProtocolStringList messages = error.getMessagesList();
        return messages.stream().collect(Collectors.joining("\n"));
    }

    public String deploy(String rhoCode, boolean useBiggerRhloPrice) throws F1r3flyDeployError {
        try {
            Security.addProvider(new Blake2bProvider());

            long phloLimit = useBiggerRhloPrice ? 5_000_000_000L : 100_000;

            // Make deployment
            CasperMessage.DeployDataProto deployment = CasperMessage.DeployDataProto.newBuilder()
                .setTerm(rhoCode)
                .setTimestamp(0)
                .setPhloPrice(1)
                .setPhloLimit(phloLimit)
                .setShardId("root")
                .build();

            // Sign deployment
            CasperMessage.DeployDataProto signed = signDeploy(deployment);
            LOGGER.debug("Signed {}", signed);

            // Deploy
            Uni<String> deployVolumeContract =
                Uni.createFrom().future(deployService.doDeploy(signed))
                    .flatMap(deployResponse -> {
                        LOGGER.debug("Deploy Response {}", deployResponse);
                        if (deployResponse.hasError()) {
                            return this.<String>fail(rhoCode, deployResponse.getError());
                        } else {
                            return succeed(deployResponse.getResult());
                        }
                    })
                    .flatMap(deployResult -> {
                        String deployId = deployResult.substring(deployResult.indexOf("DeployId is: ") + 13, deployResult.length());
                        return Uni.createFrom().future(proposeService.propose(ProposeServiceCommon.ProposeQuery.newBuilder().setIsAsync(false).build()))
                            .flatMap(proposeResponse -> {
                                LOGGER.debug("Propose Response {}", proposeResponse);
                                if (proposeResponse.hasError()) {
                                    return this.<String>fail(rhoCode, proposeResponse.getError());
                                } else {
                                    return succeed(deployId);
                                }
                            });
                    })
                    .flatMap(deployId -> {
                        ByteString b64 = ByteString.copyFrom(Hex.decode(deployId));
                        return Uni.createFrom().future(deployService.findDeploy(DeployServiceCommon.FindDeployQuery.newBuilder().setDeployId(b64).build()))
                            .flatMap(findResponse -> {
                                LOGGER.debug("Find Response {}", findResponse);
                                if (findResponse.hasError()) {
                                    return this.<String>fail(rhoCode, findResponse.getError());
                                } else {
                                    return succeed(findResponse.getBlockInfo().getBlockHash());
                                }
                            });
                    })
                    .flatMap(blockHash -> {
                        LOGGER.debug("Block Hash {}", blockHash);
                        return Uni.createFrom().future(deployService.isFinalized(DeployServiceCommon.IsFinalizedQuery.newBuilder().setHash(blockHash).build()))
                            .flatMap(isFinalizedResponse -> {
                                LOGGER.debug("isFinalizedResponse {}", isFinalizedResponse);
                                if (isFinalizedResponse.hasError() || !isFinalizedResponse.getIsFinalized()) {
                                    return fail(rhoCode, isFinalizedResponse.getError());
                                } else {
                                    return succeed(blockHash);
                                }
                            })
                            .onFailure().retry()
                            .withBackOff(INIT_DELAY, MAX_DELAY)
                            .atMost(RETRIES);
                    });

            // Drummer Hoff Fired It Off
            return deployVolumeContract.await().indefinitely();
        } catch (Exception e) {
            if (e instanceof F1r3flyDeployError) {
                throw (F1r3flyDeployError) e;
            } else {
                LOGGER.warn("failed to deploy Rho {}", rhoCode, e);
                throw new F1r3flyDeployError(rhoCode, "Failed to deploy", e);
            }
        }
    }

    public List<RhoTypes.Par> getDataAtName(String blockHash, String expr) throws NoDataByPath {
        LOGGER.info("Get data at block {} by name {}", blockHash, expr);

        RhoTypes.Par par = RhoTypes.Par.newBuilder().addExprs(
            RhoTypes.Expr.newBuilder()
                .setGString(expr)
                .build()
        ).build();

        DeployServiceCommon.DataAtNameByBlockQuery request = DeployServiceCommon.DataAtNameByBlockQuery.newBuilder()
            .setBlockHash(blockHash)
            .setPar(par)
            .build();

        casper.v1.DeployServiceV1.RhoDataResponse response = null;
        try {
            response = deployService.getDataAtName(request).get();
            LOGGER.debug("Get data at block {} by name {}. Response {}", blockHash, expr, response);
        } catch (InterruptedException | ExecutionException e) {
            LOGGER.warn("Failed to get data at block {} by name {}", blockHash, expr, e);
            throw new NoDataByPath(expr, blockHash, e);
        }


        // retries:
//        Uni<casper.v1.DeployServiceV1.RhoDataResponse> getDataAtName =
//            Uni.createFrom().future(deployService.getDataAtName(request))
//                .flatMap(getResponse -> {
//                    LOGGER.debug("Get data at block {} by name {}\". Response {}", blockHash, expr, getResponse);
//                    if (getResponse.hasError()) {
//                        return Uni.createFrom().failure(new GetDataError(blockHash, expr, new RuntimeException(gatherErrors(getResponse.getError()))));
//                    } else {
//                        return succeed(getResponse);
//                    }
//                })
//                .onFailure().retry()
//                .withBackOff(INIT_DELAY, MAX_DELAY)
//                .atMost(1); //TODO add more if needed
//
//        casper.v1.DeployServiceV1.RhoDataResponse response = getDataAtName.await().indefinitely();

        if (response.hasError()) {
            throw new NoDataByPath(expr, blockHash, new FuseException(gatherErrors(response.getError())));
        } else {
            return response
                .getPayload()
                .getParList();
        }
    }


    private CasperMessage.DeployDataProto signDeploy(CasperMessage.DeployDataProto deploy) {
        final MessageDigest digest;

        try {
            digest = MessageDigest.getInstance(Blake2b.BLAKE2_B_256);
        } catch (NoSuchAlgorithmException e) {
            throw new FuseException("Can't load MessageDigest instance (BLAKE2_B_256)", e);
        }

        final Secp256k1 secp256k1 = Secp256k1.get();

        CasperMessage.DeployDataProto.Builder builder = CasperMessage.DeployDataProto.newBuilder();

        builder
            .setTerm(deploy.getTerm())
            .setTimestamp(deploy.getTimestamp())
            .setPhloPrice(deploy.getPhloPrice())
            .setPhloLimit(deploy.getPhloLimit())
            .setValidAfterBlockNumber(deploy.getValidAfterBlockNumber())
            .setShardId(deploy.getShardId());

        CasperMessage.DeployDataProto signed = builder.build();

        byte[] serial = signed.toByteArray();
        digest.update(serial);
        byte[] hashed = digest.digest();
        byte[] signature = secp256k1.compact2der(secp256k1.sign(hashed, signingKey));
        byte[] pubKey = secp256k1.pubkeyCreate(signingKey);

        CasperMessage.DeployDataProto.Builder outbound = signed.toBuilder();
        outbound
            .setSigAlgorithm("secp256k1")
            .setSig(ByteString.copyFrom(signature))
            .setDeployer(ByteString.copyFrom(pubKey));

        return outbound.build();
    }
}
