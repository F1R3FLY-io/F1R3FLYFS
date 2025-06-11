package io.f1r3fly.f1r3drive.finderextensions;

import generic.FinderSyncExtensionServiceGrpc;
import generic.FinderSyncExtensionServiceOuterClass;
import io.f1r3fly.f1r3drive.errors.InvalidSigningKeyException;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class FinderSyncExtensionServiceServer {
    private static final Logger logger = LoggerFactory.getLogger(FinderSyncExtensionServiceServer.class);
    private Server server;

    public static class Result {
        private final boolean success;
        private final String errorMessage;
        
        private Result(boolean success, String errorMessage) {
            this.success = success;
            this.errorMessage = errorMessage;
        }
        
        public static Result success() {
            return new Result(true, null);
        }
        
        public static Result error(String message) {
            return new Result(false, message);
        }
        
        public boolean isSuccess() {
            return success;
        }
        
        public String getErrorMessage() {
            return errorMessage;
        }
    }

    public FinderSyncExtensionServiceServer(java.util.function.Function<String, Result> onChange, java.util.function.BiFunction<String, String, Result> onUnlockRevDirectory, int port) {
        server = ServerBuilder.forPort(port)
            .addService(new FinderSyncExtensionServiceImpl(onChange, onUnlockRevDirectory))
            .build();
    }

    public void start() throws IOException {
        server.start();
        logger.debug("Server started, listening on {}", server.getPort());
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.debug("Shutting down gRPC server since JVM is shutting down");
            FinderSyncExtensionServiceServer.this.stop();
            logger.debug("Server shut down");
        }));
    }

    public void stop() {
        if (server != null && !server.isTerminated()) {
            logger.debug("Shutting down gRPC server");
            server.shutdown();
        }
    }

    static class FinderSyncExtensionServiceImpl extends FinderSyncExtensionServiceGrpc.FinderSyncExtensionServiceImplBase {
        private final java.util.function.Function<String, Result> onChange;
        private final java.util.function.BiFunction<String, String, Result> onUnlockRevDirectory;

        public FinderSyncExtensionServiceImpl(java.util.function.Function<String, Result> onChange, java.util.function.BiFunction<String, String, Result> onUnlockRevDirectory) {
            this.onChange = onChange;
            this.onUnlockRevDirectory = onUnlockRevDirectory;
        }

        @Override
        public void submitAction(FinderSyncExtensionServiceOuterClass.MenuActionRequest request, StreamObserver<FinderSyncExtensionServiceOuterClass.Response> responseObserver) {
            switch(request.getAction()) {
                case CHANGE:
                    if (request.getPathCount() != 1) {
                        responseObserver.onNext(FinderSyncExtensionServiceOuterClass.Response.newBuilder().setError(
                            FinderSyncExtensionServiceOuterClass.ErrorResponse.newBuilder().setErrorMessage("Path should be one").build()
                        ).build());
                        responseObserver.onCompleted();
                        return;
                    } else {
                        logger.info("Received Change: path={}, action={}", request.getPath(0), request.getAction());
                        Result result = onChange.apply(request.getPath(0));
                        if (result.isSuccess()) {
                            responseObserver.onNext(FinderSyncExtensionServiceOuterClass.Response.newBuilder().setSuccess(
                                FinderSyncExtensionServiceOuterClass.EmptyResponse.newBuilder().build()
                            ).build());
                        } else {
                            logger.error("Error during change operation for path: {} - {}", request.getPath(0), result.getErrorMessage());
                            responseObserver.onNext(FinderSyncExtensionServiceOuterClass.Response.newBuilder().setError(
                                FinderSyncExtensionServiceOuterClass.ErrorResponse.newBuilder()
                                    .setErrorMessage("Change operation failed: " + result.getErrorMessage())
                                    .build()
                            ).build());
                        }
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
        public void unlockWalletDirectory(FinderSyncExtensionServiceOuterClass.UnlockWalletDirectoryRequest request, StreamObserver<FinderSyncExtensionServiceOuterClass.Response> responseObserver) {
            logger.info("UnlockWalletDirectory called with revAddress: {}, privateKey: {}", request.getRevAddress(), request.getPrivateKey());

            try {
                Result result = onUnlockRevDirectory.apply(request.getRevAddress(), request.getPrivateKey());
                if (result.isSuccess()) {
                    responseObserver.onNext(FinderSyncExtensionServiceOuterClass.Response.newBuilder().setSuccess(
                        FinderSyncExtensionServiceOuterClass.EmptyResponse.newBuilder().build()
                    ).build());
                } else {
                    responseObserver.onNext(FinderSyncExtensionServiceOuterClass.Response.newBuilder().setError(
                        FinderSyncExtensionServiceOuterClass.ErrorResponse.newBuilder()
                            .setErrorMessage("Failed to unlock directory: " + result.getErrorMessage())
                            .build()
                    ).build());
                }
            
            } catch (InvalidSigningKeyException e) {
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