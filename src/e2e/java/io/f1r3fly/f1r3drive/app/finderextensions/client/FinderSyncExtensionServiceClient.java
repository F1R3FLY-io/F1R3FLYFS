package io.f1r3fly.f1r3drive.app.finderextensions.client;

import generic.FinderSyncExtensionServiceGrpc;
import generic.FinderSyncExtensionServiceOuterClass;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FinderSyncExtensionServiceClient implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(FinderSyncExtensionServiceClient.class);
    private final FinderSyncExtensionServiceGrpc.FinderSyncExtensionServiceBlockingStub blockingStub;
    private final ManagedChannel channel;

    public static class FinderSyncServiceException extends Exception {
        public FinderSyncServiceException(String message) {
            super(message);
        }
        
        public FinderSyncServiceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    public static class ActionSubmissionException extends FinderSyncServiceException {
        public ActionSubmissionException(String message) {
            super(message);
        }
        
        public ActionSubmissionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    public static class WalletUnlockException extends FinderSyncServiceException {
        public WalletUnlockException(String message) {
            super(message);
        }
        
        public WalletUnlockException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public FinderSyncExtensionServiceClient(String host, int port) {
        channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();
        blockingStub = FinderSyncExtensionServiceGrpc.newBlockingStub(channel);
    }

    public void submitAction(FinderSyncExtensionServiceOuterClass.MenuActionType action, String... paths) throws ActionSubmissionException {
        FinderSyncExtensionServiceOuterClass.MenuActionRequest request = FinderSyncExtensionServiceOuterClass.MenuActionRequest.newBuilder()
                .setAction(action)
                .addAllPath(java.util.Arrays.asList(paths))
                .build();

        try {
            FinderSyncExtensionServiceOuterClass.Response response = blockingStub.submitAction(request);
            if (response.hasError()) {
                String errorMessage = response.getError().getErrorMessage();
                logger.error("Server returned error for action {}: {}", action, errorMessage);
                throw new ActionSubmissionException("Failed to submit action " + action + ": " + errorMessage);
            }
            logger.debug("Successfully submitted action {} for paths: {}", action, java.util.Arrays.toString(paths));
        } catch (StatusRuntimeException e) {
            logger.error("gRPC error calling submitAction for action {}: {}", action, e.getStatus(), e);
            throw new ActionSubmissionException("gRPC communication error: " + e.getStatus().getDescription(), e);
        } catch (ActionSubmissionException e) {
            // Re-throw our custom exceptions
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error calling submitAction for action {}", action, e);
            throw new ActionSubmissionException("Unexpected error during action submission: " + e.getMessage(), e);
        }
    }

    public void unlockWalletDirectory(String revAddress, String privateKey) throws WalletUnlockException {
        FinderSyncExtensionServiceOuterClass.UnlockWalletDirectoryRequest request = FinderSyncExtensionServiceOuterClass.UnlockWalletDirectoryRequest.newBuilder()
                .setRevAddress(revAddress)
                .setPrivateKey(privateKey)
                .build();

        try {
            FinderSyncExtensionServiceOuterClass.Response response = blockingStub.unlockWalletDirectory(request);
            if (response.hasError()) {
                String errorMessage = response.getError().getErrorMessage();
                logger.error("Server returned error for wallet unlock (revAddress: {}): {}", revAddress, errorMessage);
                throw new WalletUnlockException("Failed to unlock wallet folder for " + revAddress + ": " + errorMessage);
            }
            logger.debug("Successfully unlocked wallet folder for revAddress: {}", revAddress);
        } catch (StatusRuntimeException e) {
            logger.error("gRPC error calling UnlockWalletDirectory for revAddress {}: {}", revAddress, e.getStatus(), e);
            throw new WalletUnlockException("gRPC communication error: " + e.getStatus().getDescription(), e);
        } catch (WalletUnlockException e) {
            // Re-throw our custom exceptions
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error calling UnlockWalletDirectory for revAddress {}", revAddress, e);
            throw new WalletUnlockException("Unexpected error during wallet unlock: " + e.getMessage(), e);
        }
    }

    @Override
    public void close() {
        try {
            channel.shutdown().awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            logger.error("Error shutting down channel", e);
            Thread.currentThread().interrupt();
        }
    }
} 