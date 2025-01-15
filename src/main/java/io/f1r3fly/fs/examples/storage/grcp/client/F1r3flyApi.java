package io.f1r3fly.fs.examples.storage.grcp.client;

import casper.CasperMessage;
import casper.DeployServiceCommon;
import casper.ProposeServiceCommon;
import casper.v1.DeployServiceGrpc;
import casper.v1.DeployServiceV1;
import casper.v1.ProposeServiceGrpc;
import com.google.protobuf.ByteString;
import com.google.protobuf.ProtocolStringList;
import com.rfksystems.blake2b.Blake2b;
import com.rfksystems.blake2b.security.Blake2bProvider;
import io.f1r3fly.fs.FuseException;
import fr.acinq.secp256k1.Hex;
import fr.acinq.secp256k1.Secp256k1;
import io.f1r3fly.fs.examples.storage.errors.AnotherProposalInProgressError;
import io.f1r3fly.fs.examples.storage.errors.F1r3flyDeployError;
import io.f1r3fly.fs.examples.storage.errors.NoDataByPath;
import io.f1r3fly.fs.examples.storage.errors.NoNewDeploysError;
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
    public static final String RHOLANG = "rholang";
    public static final String METTA_LANGUAGE = "metta";

    private static final Logger log = LoggerFactory.getLogger(F1r3flyApi.class);

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

        Security.addProvider(new Blake2bProvider());

        ManagedChannel channel = ManagedChannelBuilder.forAddress(nodeHost, grpcPort).usePlaintext().build();

        this.signingKey = signingKey;
        this.deployService = DeployServiceGrpc.newFutureStub(channel)
            .withMaxInboundMessageSize(MAX_MESSAGE_SIZE)
            .withMaxOutboundMessageSize(MAX_MESSAGE_SIZE);
        this.proposeService = ProposeServiceGrpc.newFutureStub(channel)
            .withMaxInboundMessageSize(MAX_MESSAGE_SIZE)
            .withMaxOutboundMessageSize(MAX_MESSAGE_SIZE);

        // add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down gRPC channel");
            channel.shutdown();
        }));
    }

    // Cut down on verbosity of surfacing successes
    private <T> Uni<T> succeed(T t) {
        return Uni.createFrom().item(t);
    }

    // Cut down on verbosity of surfacing errors
    private <T> Uni<T> fail(String deployId, ServiceErrorOuterClass.ServiceError error) {
        return Uni.createFrom().failure(new F1r3flyDeployError(deployId, gatherErrors(error)));
    }

    private String gatherErrors(ServiceErrorOuterClass.ServiceError error) {
        ProtocolStringList messages = error.getMessagesList();
        return messages.stream().collect(Collectors.joining("\n"));
    }

    public String deploy(String rhoCode, boolean useBiggerRhloPrice, String language) throws F1r3flyDeployError, NoNewDeploysError {
        try {

            int maxRholangInLogs = 2000;
            log.debug("Rholang code {}", rhoCode.length() > maxRholangInLogs ? rhoCode.substring(0, maxRholangInLogs) : rhoCode);

            long phloLimit = useBiggerRhloPrice ? 5_000_000_000L : 50_000L;

            // Make deployment
            CasperMessage.DeployDataProto deployment = CasperMessage.DeployDataProto.newBuilder()
                .setTerm(rhoCode)
                .setTimestamp(0)
                .setPhloPrice(1)
                .setPhloLimit(phloLimit)
                .setShardId("root")
                .setLanguage(language)
                .build();

            // Sign deployment
            CasperMessage.DeployDataProto signed = signDeploy(deployment);

            // Deploy
            DeployServiceV1.DeployResponse deployResponse = deployService.doDeploy(signed).get();
            String deployResult = deployResponse.getResult();

            log.info("Deploy result: {}", deployResult);

            if (deployResult.contains("Success")) {
                return deployResult.substring(deployResult.indexOf("DeployId is: ") + 13);
            } else {
                log.warn("Failed to deploy. Deploy result: {}", deployResult);
                throw new F1r3flyDeployError("Invalid deployment", deployResult);
            }
        } catch (InterruptedException | ExecutionException e) {
            if (e.getMessage().contains("NoNewDeploys")) {
                throw new NoNewDeploysError("No new deploys found", "", e);
            } else {
                throw new F1r3flyDeployError("", "Failed to deploy", e);
            }
        }
    }

    public String propose(String deployId) throws NoNewDeploysError, F1r3flyDeployError, AnotherProposalInProgressError {
        try {
            return Uni.createFrom().future(proposeService.propose(ProposeServiceCommon.ProposeQuery.newBuilder().setIsAsync(false).build()))
                .flatMap(proposeResponse -> {
                    if (proposeResponse.hasError()) {
                        return this.fail(deployId, proposeResponse.getError());
                    } else {
                        return Uni.createFrom().voidItem();
                    }
                })
                .flatMap((Void) -> {
                    ByteString b64 = ByteString.copyFrom(Hex.decode(deployId));
                    return Uni.createFrom().future(deployService.findDeploy(DeployServiceCommon.FindDeployQuery.newBuilder().setDeployId(b64).build()))
                        .flatMap(findResponse -> {
                            log.trace("Find Response {}", findResponse);
                            if (findResponse.hasError()) {
                                return this.<String>fail(deployId, findResponse.getError());
                            } else {
                                return succeed(findResponse.getBlockInfo().getBlockHash());
                            }
                        });
                })
                .flatMap(blockHash -> {
                    log.debug("Block Hash {}", blockHash);
                    return Uni.createFrom().future(deployService.isFinalized(DeployServiceCommon.IsFinalizedQuery.newBuilder().setHash(blockHash).build()))
                        .flatMap(isFinalizedResponse -> {
                            log.debug("isFinalizedResponse {}", isFinalizedResponse);
                            if (isFinalizedResponse.hasError() || !isFinalizedResponse.getIsFinalized()) {
                                return fail(deployId, isFinalizedResponse.getError());
                            } else {
                                return succeed(blockHash);
                            }
                        })
                        .onFailure().retry()
                        .withBackOff(INIT_DELAY, MAX_DELAY)
                        .atMost(RETRIES);
                })
                .await().indefinitely();
        } catch (Throwable e) {
            if (e.getMessage().contains("NoNewDeploys")) {
                throw new NoNewDeploysError("No new deploys found", deployId, e);
            } else if (e.getMessage().contains("propose is in progress") || e.getCause().getMessage().contains("propose is in progress")) {
                throw new AnotherProposalInProgressError(e);
            } else {
                throw new F1r3flyDeployError(deployId, "Failed to deploy", e);
            }
        }
    }

    public List<RhoTypes.Par> findDataByName(String expr) throws NoDataByPath {
        log.info("Find data by name {}", expr);

        RhoTypes.Par par = RhoTypes.Par.newBuilder().addExprs(
            RhoTypes.Expr.newBuilder()
                .setGString(expr)
                .build()
        ).build();

//        int MAX_DEPTH = 1000;
        int MAX_DEPTH = 50;

        DeployServiceCommon.DataAtNameQuery request = DeployServiceCommon.DataAtNameQuery.newBuilder()
            .setName(par)
            .setDepth(MAX_DEPTH)
            .build();

        DeployServiceV1.ListeningNameDataResponse response = null;
        try {
            response = deployService.listenForDataAtName(request).get();
            log.debug("Find data by name {}. Is error response = {}", expr, response.hasError());
        } catch (InterruptedException | ExecutionException e) {
            log.warn("Failed to find data by name {}", expr, e);
            throw new NoDataByPath(expr, "", e);
        }

        if (response.hasError()) {
            log.debug("Get data by name {}. Error response {}", expr, response.getError());
            throw new NoDataByPath(expr, new FuseException(gatherErrors(response.getError())));
        } else if (response.getPayload().getLength() == 0) {
            log.debug("Get data at by name {}. No data found (an empty list of block returned)", expr);
            throw new NoDataByPath(expr);
        } else {
            DeployServiceV1.ListeningNameDataPayload responsePayload = response.getPayload();

            // get data from last block
            List<DeployServiceCommon.DataWithBlockInfo> blockInfoList = responsePayload
                .getBlockInfoList();
//                .stream()
//                .sorted((x1, x2) -> Long.compare(x2.getPostBlockDataList().size(), x1.getPostBlockDataList().size()))
//                .toList();

            log.info("Get data by name {}. Found {} blocks", expr, blockInfoList.size());

            return blockInfoList
                .get(0)
                .getPostBlockDataList();
        }
    }

    public List<RhoTypes.Par> getDataAtBlockByName(String blockHash, String expr) throws NoDataByPath {
        log.info("Get data at block {} by name {}", blockHash, expr);

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
            log.debug("Get data at block {} by name {}. Is error response = {}", blockHash, expr, response.hasError());
        } catch (InterruptedException | ExecutionException e) {
            log.warn("Failed to get data at block {} by name {}", blockHash, expr, e);
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
