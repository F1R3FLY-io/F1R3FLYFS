package coop.f1r3fly.fs.examples;

import casper.CasperMessage;
import casper.DeployServiceCommon;
import casper.ProposeServiceCommon;
import casper.v1.DeployServiceGrpc;
import casper.v1.ProposeServiceGrpc;
import com.google.protobuf.ByteString;
import com.google.protobuf.ProtocolStringList;
import com.rfksystems.blake2b.Blake2b;
import com.rfksystems.blake2b.security.Blake2bProvider;
import fr.acinq.secp256k1.Hex;
import fr.acinq.secp256k1.Secp256k1;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.smallrye.mutiny.Uni;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import servicemodelapi.ServiceErrorOuterClass;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.time.Duration;
import java.util.stream.Collectors;

public class F1r3flyDeployer {
    private static final Logger LOGGER = LoggerFactory.getLogger(F1r3flyDeployer.class);

    private static final Duration INIT_DELAY = Duration.ofMillis(100);
    private static final Duration MAX_DELAY = Duration.ofSeconds(5);
    private static final int RETRIES = 10;

    private final byte[] signingKey;
    private final DeployServiceGrpc.DeployServiceFutureStub deployService;
    private final ProposeServiceGrpc.ProposeServiceFutureStub proposeService;


    public F1r3flyDeployer(byte[] signingKey,
                    String nodeHost,
                    int grpcPort
    ) {
        super();


        ManagedChannel channel = ManagedChannelBuilder.forAddress(nodeHost, grpcPort).usePlaintext().build();

        this.signingKey = signingKey;
        this.deployService = DeployServiceGrpc.newFutureStub(channel);
        this.proposeService = ProposeServiceGrpc.newFutureStub(channel);
    }

    // Cut down on verbosity of surfacing successes
    private <T> Uni<T> succeed(T t) {
        return Uni.createFrom().item(t);
    }

    // Cut down on verbosity of surfacing errors
    private <T> Uni<T> fail(ServiceErrorOuterClass.ServiceError error) {
        return Uni.createFrom().failure(new RuntimeException(gatherErrors(error)));
    }

    private String gatherErrors(ServiceErrorOuterClass.ServiceError error) {
        ProtocolStringList messages = error.getMessagesList();
        return messages.stream().collect(Collectors.joining("\n"));
    }

    /*
     * Method to deploy a Rholang contract, and then get results from the deployment.
     * 1. doDeploy
     * 2. propose
     * 3. findDeployment
     * 3. isFinalized (poll)
     * 4. ???
     * 5. getDataAtName
     */

    void executeAndGet(String rhoCode) {
        Security.addProvider(new Blake2bProvider());

        // Make deployment
        CasperMessage.DeployDataProto deployment = CasperMessage.DeployDataProto.newBuilder()
            .setTerm(rhoCode)
            .setTimestamp(0)
            .setPhloPrice(1)
            .setPhloLimit(1000000)
            .setShardId("root")
            .build();

        // Sign deployment
        CasperMessage.DeployDataProto signed = signDeploy(deployment);
        LOGGER.debug("Signed \n{}", signed);

        // Deploy
        Uni<Void> deployVolumeContract =
            Uni.createFrom().future(deployService.doDeploy(signed))
                .flatMap(deployResponse -> {
                    LOGGER.debug("Deploy Response \n{}", deployResponse);
                    if (deployResponse.hasError()) {
                        return this.<String>fail(deployResponse.getError());
                    } else {
                        return succeed(deployResponse.getResult());
                    }
                })
                .flatMap(deployResult -> {
                    String deployId = deployResult.substring(deployResult.indexOf("DeployId is: ") + 13, deployResult.length());
                    return Uni.createFrom().future(proposeService.propose(ProposeServiceCommon.ProposeQuery.newBuilder().setIsAsync(false).build()))
                        .flatMap(proposeResponse -> {
                            LOGGER.debug("Propose Response \n{}", proposeResponse);
                            if (proposeResponse.hasError()) {
                                return this.<String>fail(proposeResponse.getError());
                            } else {
                                return succeed(deployId);
                            }
                        });
                })
                .flatMap(deployId -> {
                    ByteString b64 = ByteString.copyFrom(Hex.decode(deployId));
                    return Uni.createFrom().future(deployService.findDeploy(DeployServiceCommon.FindDeployQuery.newBuilder().setDeployId(b64).build()))
                        .flatMap(findResponse -> {
                            LOGGER.debug("Find Response \n{}", findResponse);
                            if (findResponse.hasError()) {
                                return this.<String>fail(findResponse.getError());
                            } else {
                                return succeed(findResponse.getBlockInfo().getBlockHash());
                            }
                        });
                })
                .flatMap(blockHash -> {
                    LOGGER.debug("Block Hash \n{}", blockHash);
                    return Uni.createFrom().future(deployService.isFinalized(DeployServiceCommon.IsFinalizedQuery.newBuilder().setHash(blockHash).build()))
                        .flatMap(isFinalizedResponse -> {
                            LOGGER.debug("isFinalizedResponse \n{}", isFinalizedResponse);
                            if (isFinalizedResponse.hasError() || !isFinalizedResponse.getIsFinalized()) {
                                return fail(isFinalizedResponse.getError());
                            } else {
                                return Uni.createFrom().voidItem();
                            }
                        })
                        .onFailure().retry()
                        .withBackOff(INIT_DELAY, MAX_DELAY)
                        .atMost(RETRIES);
                });

        // Drummer Hoff Fired It Off
        deployVolumeContract.await().indefinitely();
    }


    // public for use by clients of the filesystem, e.g. tests
    public CasperMessage.DeployDataProto signDeploy(CasperMessage.DeployDataProto deploy) {
        final MessageDigest digest;

        try {
            digest = MessageDigest.getInstance(Blake2b.BLAKE2_B_256);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Can't load MessageDigest instance (BLAKE2_B_256)", e);
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
