package io.f1r3fly.f1r3drive.blockchain.client;

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
import io.f1r3fly.f1r3drive.fuse.FuseException;
import fr.acinq.secp256k1.Hex;
import fr.acinq.secp256k1.Secp256k1;
import io.f1r3fly.f1r3drive.errors.F1r3flyDeployError;
import io.f1r3fly.f1r3drive.errors.F1r3flyFSError;
import io.f1r3fly.f1r3drive.errors.NoDataByPath;
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

public class F1r3flyBlockchainClient {
    public static final String RHOLANG = "rholang";
    public static final String METTA_LANGUAGE = "metta";

    private static final Logger LOGGER = LoggerFactory.getLogger(F1r3flyBlockchainClient.class);

    private static final Duration INIT_DELAY = Duration.ofMillis(100);
    private static final Duration MAX_DELAY = Duration.ofSeconds(5);
    private static final int RETRIES = 10;
    private static final int MAX_MESSAGE_SIZE = Integer.MAX_VALUE; // ~2 GB

    private final DeployServiceGrpc.DeployServiceFutureStub validatorDeployService;
    private final ProposeServiceGrpc.ProposeServiceFutureStub validatorProposeService;
    private final DeployServiceGrpc.DeployServiceFutureStub observerDeployService;


    public F1r3flyBlockchainClient(String validatorHost,
                                   int validatorPort,
                                   String observerHost,
                                   int observerPort
    ) {
        super();

        Security.addProvider(new Blake2bProvider());

        ManagedChannel validatorChannel = ManagedChannelBuilder.forAddress(validatorHost, validatorPort).usePlaintext().build();

        this.validatorDeployService = DeployServiceGrpc.newFutureStub(validatorChannel)
            .withMaxInboundMessageSize(MAX_MESSAGE_SIZE)
            .withMaxOutboundMessageSize(MAX_MESSAGE_SIZE);
        this.validatorProposeService = ProposeServiceGrpc.newFutureStub(validatorChannel)
            .withMaxInboundMessageSize(MAX_MESSAGE_SIZE)
            .withMaxOutboundMessageSize(MAX_MESSAGE_SIZE);

        ManagedChannel observerChannel = ManagedChannelBuilder.forAddress(observerHost, observerPort).usePlaintext().build();

        this.observerDeployService = DeployServiceGrpc.newFutureStub(observerChannel)
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

    public DeployServiceCommon.BlockInfo getGenesisBlock() throws F1r3flyFSError {
        DeployServiceV1.LastFinalizedBlockResponse response = null;
            try {
                response = observerDeployService.lastFinalizedBlock(DeployServiceCommon.LastFinalizedBlockQuery.newBuilder().build()).get();
            } catch (InterruptedException | ExecutionException e) {
                LOGGER.error("Error retrieving last finalized block", e);
                throw new F1r3flyFSError("Error retrieving last finalized block", e);
            }

        DeployServiceCommon.BlockInfo block = response.getBlockInfo();

        while (block.getBlockInfo().getBlockNumber() > 0) {
            try {
                block = observerDeployService.getBlock(
                    DeployServiceCommon.BlockQuery.newBuilder()
                        .setHash(block.getBlockInfo().getParentsHashList(0))
                        .build()
                ).get().getBlockInfo();
            } catch (InterruptedException | ExecutionException e) {
                LOGGER.error("Error retrieving block information", e);
                throw new F1r3flyFSError("Error retrieving block information", e);
            }
        }

        return block;
    }

    public RhoTypes.Expr exploratoryDeploy(String rhoCode) throws F1r3flyFSError {
        try {
            LOGGER.debug("Exploratory deploy code {}", rhoCode);

            // Create query
            DeployServiceCommon.ExploratoryDeployQuery exploratoryDeploy =
                DeployServiceCommon.ExploratoryDeployQuery.newBuilder()
                .setTerm(rhoCode)
                .build();

            // Deploy
            DeployServiceV1.ExploratoryDeployResponse deployResponse =
                observerDeployService.exploratoryDeploy(exploratoryDeploy).get();

            if (deployResponse.hasError()) {
                LOGGER.debug("Exploratory deploy code {}. Error response {}", rhoCode, deployResponse.getError());
                throw new F1r3flyFSError("Error retrieving exploratory deploy: " + gatherErrors(deployResponse.getError()));
            }

            return deployResponse.getResult().getPostBlockData(0).getExprs(0);
        } catch (Exception e) {
            LOGGER.warn("failed to deploy exploratory code", e);
            throw new F1r3flyFSError("Error deploying exploratory code", e);
        }
    }

    public String deploy(String rhoCode, boolean useBiggerRhloPrice, String language, byte[] signingKey) throws F1r3flyDeployError {
        try {

            int maxRholangInLogs = 2000;
            LOGGER.debug("Rholang code {}", rhoCode.length() > maxRholangInLogs ? rhoCode.substring(0, maxRholangInLogs) : rhoCode);

            long phloLimit = useBiggerRhloPrice ? 5_000_000_000L : 50_000L;

            LOGGER.trace("Language parameter is skipped for now: {}. Using default language: {}", language, RHOLANG);

            // Make deployment
            CasperMessage.DeployDataProto deployment = CasperMessage.DeployDataProto.newBuilder()
                .setTerm(rhoCode)
                .setTimestamp(0)
                .setPhloPrice(1)
                .setPhloLimit(phloLimit)
                .setShardId("root")
                // .setLanguage(language)
                .build();

            // Sign deployment
            CasperMessage.DeployDataProto signed = signDeploy(deployment, signingKey);

            // Deploy
            Uni<String> deployVolumeContract =
                Uni.createFrom().future(validatorDeployService.doDeploy(signed))
                    .flatMap(deployResponse -> {
//                        LOGGER.trace("Deploy Response {}", deployResponse);
                        if (deployResponse.hasError()) {
                            return this.<String>fail(rhoCode, deployResponse.getError());
                        } else {
                            return succeed(deployResponse.getResult());
                        }
                    })
                    .flatMap(deployResult -> {
                        String deployId = deployResult.substring(deployResult.indexOf("DeployId is: ") + 13, deployResult.length());
                        return Uni.createFrom().future(validatorProposeService.propose(ProposeServiceCommon.ProposeQuery.newBuilder().setIsAsync(false).build()))
                            .flatMap(proposeResponse -> {
//                                LOGGER.debug("Propose Response {}", proposeResponse);
                                if (proposeResponse.hasError()) {
                                    return this.<String>fail(rhoCode, proposeResponse.getError());
                                } else {
                                    return succeed(deployId);
                                }
                            });
                    })
                    .flatMap(deployId -> {
                        ByteString b64 = ByteString.copyFrom(Hex.decode(deployId));
                        return Uni.createFrom().future(validatorDeployService.findDeploy(DeployServiceCommon.FindDeployQuery.newBuilder().setDeployId(b64).build()))
                            .flatMap(findResponse -> {
//                                LOGGER.debug("Find Response {}", findResponse);
                                if (findResponse.hasError()) {
                                    return this.<String>fail(rhoCode, findResponse.getError());
                                } else {
                                    return succeed(findResponse.getBlockInfo().getBlockHash());
                                }
                            });
                    })
                    .flatMap(blockHash -> {
                        LOGGER.debug("Block Hash {}", blockHash);
                        return Uni.createFrom().future(validatorDeployService.isFinalized(DeployServiceCommon.IsFinalizedQuery.newBuilder().setHash(blockHash).build()))
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

    public List<RhoTypes.Par> findDataByName(String expr) throws NoDataByPath {
        LOGGER.info("Find data by name {}", expr);

        RhoTypes.Par par = RhoTypes.Par.newBuilder().addExprs(
            RhoTypes.Expr.newBuilder()
                .setGString(expr)
                .build()
        ).build();

        int MAX_DEPTH = 50;

        DeployServiceCommon.DataAtNameQuery request = DeployServiceCommon.DataAtNameQuery.newBuilder()
            .setName(par)
            .setDepth(MAX_DEPTH)
            .build();

        DeployServiceV1.ListeningNameDataResponse response = null;
        try {
            response = validatorDeployService.listenForDataAtName(request).get();
            LOGGER.debug("Find data by name {}. Is error response = {}", expr, response.hasError());
        } catch (InterruptedException | ExecutionException e) {
            LOGGER.warn("Failed to find data by name {}", expr, e);
            throw new NoDataByPath(expr, "", e);
        }

        if (response.hasError()) {
            LOGGER.debug("Get data by name {}. Error response {}", expr, response.getError());
            throw new NoDataByPath(expr, new FuseException(gatherErrors(response.getError())));
        } else if (response.getPayload().getLength() == 0) {
            LOGGER.debug("Get data at by name {}. No data found (an empty list of block returned)", expr);
            throw new NoDataByPath(expr);
        } else {
            DeployServiceV1.ListeningNameDataPayload responsePayload = response.getPayload();

            // get data from last block
            return responsePayload
                .getBlockInfoList()
                .get(0)
                .getPostBlockDataList();
        }
    }

    public List<RhoTypes.Par> getDataAtBlockByName(String blockHash, String expr) throws NoDataByPath {
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
            response = validatorDeployService.getDataAtName(request).get();
            LOGGER.debug("Get data at block {} by name {}. Is error response = {}", blockHash, expr, response.hasError());
        } catch (InterruptedException | ExecutionException e) {
            LOGGER.warn("Failed to get data at block {} by name {}", blockHash, expr, e);
            throw new NoDataByPath(expr, blockHash, e);
        }


        // retries:
//        Uni<casper.v1.DeployServiceV1.RhoDataResponse> getDataAtName =
//            Uni.createFrom().future(validatorDeployService.getDataAtName(request))
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


    private CasperMessage.DeployDataProto signDeploy(CasperMessage.DeployDataProto deploy, byte[] signingKey) {
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
