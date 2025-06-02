package io.f1r3fly.fs.contextmenu;

import generic.FinderSyncExtensionServiceGrpc;
import generic.FinderSyncExtensionServiceOuterClass;
import io.f1r3fly.fs.examples.storage.filesystem.local.LockedRemoteDirectory;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class FinderSyncExtensionServiceServer {
    private static final Logger logger = LoggerFactory.getLogger(FinderSyncExtensionServiceServer.class);
    private Server server;

    public FinderSyncExtensionServiceServer(java.util.function.Consumer<String> onExchange, java.util.function.BiConsumer<String, String> onUnlockRevDirectory, int port) {
        server = ServerBuilder.forPort(port)
            .addService(new FinderSyncExtensionServiceImpl(onExchange, onUnlockRevDirectory))
            .build();
    }

    public void start() throws IOException {
        server.start();
        logger.info("Server started, listening on {}", server.getPort());
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down gRPC server since JVM is shutting down");
            FinderSyncExtensionServiceServer.this.stop();
            logger.info("Server shut down");
        }));
    }

    public void stop() {
        logger.info("Shutting down gRPC server");
        if (server != null && !server.isTerminated()) {
            logger.info("Stopping gRPC server");
            server.shutdown();
            logger.info("GRPC server shut down");
        } else {
            logger.info("GRPC server is already shut down");
        }
    }

    static class FinderSyncExtensionServiceImpl extends FinderSyncExtensionServiceGrpc.FinderSyncExtensionServiceImplBase {
        private final java.util.function.Consumer<String> onExchange;
        private final java.util.function.BiConsumer<String, String> onUnlockRevDirectory;

        public FinderSyncExtensionServiceImpl(java.util.function.Consumer<String> onExchange, java.util.function.BiConsumer<String, String> onUnlockRevDirectory) {
            this.onExchange = onExchange;
            this.onUnlockRevDirectory = onUnlockRevDirectory;
        }

        @Override
        public void submitAction(FinderSyncExtensionServiceOuterClass.MenuActionRequest request, StreamObserver<FinderSyncExtensionServiceOuterClass.Response> responseObserver) {
            switch(request.getAction()) {
                case EXCHANGE:
                    if (request.getPathCount() != 1) {
                        responseObserver.onNext(FinderSyncExtensionServiceOuterClass.Response.newBuilder().setError(
                            FinderSyncExtensionServiceOuterClass.ErrorResponse.newBuilder().setErrorMessage("Path should be one").build()
                        ).build());
                        responseObserver.onCompleted();
                        return;
                    } else {
                        logger.info("Received Change: path={}, action={}", request.getPath(0), request.getAction());
                        onExchange.accept(request.getPath(0));
                        responseObserver.onNext(FinderSyncExtensionServiceOuterClass.Response.newBuilder().setSuccess(
                            FinderSyncExtensionServiceOuterClass.EmptyResponse.newBuilder().build()
                        ).build());
                    }
                    break;
                case COMBINE:
                    // Implement combine logic if needed
                    responseObserver.onNext(FinderSyncExtensionServiceOuterClass.Response.newBuilder().setSuccess(
                        FinderSyncExtensionServiceOuterClass.EmptyResponse.newBuilder().build()
                    ).build());
                    break;
                default:
                    responseObserver.onNext(FinderSyncExtensionServiceOuterClass.Response.newBuilder().setError(
                        FinderSyncExtensionServiceOuterClass.ErrorResponse.newBuilder().setErrorMessage("Unknown action: " + request.getAction()).build()
                    ).build());
            }
            responseObserver.onCompleted();
        }

        @Override
        public void unlockWalletFolder(FinderSyncExtensionServiceOuterClass.UnlockWalletFolderRequest request, StreamObserver<FinderSyncExtensionServiceOuterClass.Response> responseObserver) {
            logger.info("UnlockWalletFolder called with revAddress: {}, privateKey: {}", request.getRevAddress(), request.getPrivateKey());

            try {
                onUnlockRevDirectory.accept(request.getRevAddress(), request.getPrivateKey());

                responseObserver.onNext(FinderSyncExtensionServiceOuterClass.Response.newBuilder().setSuccess(
                    FinderSyncExtensionServiceOuterClass.EmptyResponse.newBuilder().build()
                ).build());
            } catch (LockedRemoteDirectory.InvalidSigningKeyException e) {
                logger.error("Invalid signing key format for rev address: {} - {}", request.getRevAddress(), e.getMessage());
                responseObserver.onNext(FinderSyncExtensionServiceOuterClass.Response.newBuilder().setError(
                    FinderSyncExtensionServiceOuterClass.ErrorResponse.newBuilder()
                        .setErrorMessage("Invalid signing key format: " + e.getMessage())
                        .build()
                ).build());
            } catch (Throwable e) {
                logger.error("Error unlocking rev directory for address: {} - {}", request.getRevAddress(), e.getMessage(), e);
                responseObserver.onNext(FinderSyncExtensionServiceOuterClass.Response.newBuilder().setError(
                    FinderSyncExtensionServiceOuterClass.ErrorResponse.newBuilder()
                        .setErrorMessage("Failed to unlock directory: " + e.getMessage())
                        .build()
                ).build());
            } finally {
                responseObserver.onCompleted();
            }
        }
    }
}