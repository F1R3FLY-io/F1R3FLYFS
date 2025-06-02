package io.f1r3fly.f1r3drive.app.contextmenu.client;

import generic.FinderSyncExtensionServiceGrpc;
import generic.FinderSyncExtensionServiceOuterClass;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FinderSyncExtensionServiceClient implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(FinderSyncExtensionServiceClient.class);
    private final FinderSyncExtensionServiceGrpc.FinderSyncExtensionServiceBlockingStub blockingStub;
    private final ManagedChannel channel;

    public FinderSyncExtensionServiceClient(String host, int port) {
        channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();
        blockingStub = FinderSyncExtensionServiceGrpc.newBlockingStub(channel);
    }

    public void submitAction(FinderSyncExtensionServiceOuterClass.MenuActionType action, String... paths) {
        FinderSyncExtensionServiceOuterClass.MenuActionRequest request = FinderSyncExtensionServiceOuterClass.MenuActionRequest.newBuilder()
                .setAction(action)
                .addAllPath(java.util.Arrays.asList(paths))
                .build();

        try {
            FinderSyncExtensionServiceOuterClass.Response response = blockingStub.submitAction(request);
            if (response.hasError()) {
                logger.error("Error submitting action: {}", response.getError().getErrorMessage());
                throw new RuntimeException("Error submitting action: " + response.getError().getErrorMessage());
            }
        } catch (Exception e) {
            logger.error("Error calling submitAction", e);
            throw new RuntimeException("Error calling submitAction", e);
        }
    }

    public void unlockWalletFolder(String revAddress, String privateKey) {
        FinderSyncExtensionServiceOuterClass.UnlockWalletFolderRequest request = FinderSyncExtensionServiceOuterClass.UnlockWalletFolderRequest.newBuilder()
                .setRevAddress(revAddress)
                .setPrivateKey(privateKey)
                .build();

        try {
            FinderSyncExtensionServiceOuterClass.Response response = blockingStub.unlockWalletFolder(request);
            if (response.hasError()) {
                logger.error("Error unlocking wallet folder: {}", response.getError().getErrorMessage());
                throw new RuntimeException("Error unlocking wallet folder: " + response.getError().getErrorMessage());
            }
        } catch (Exception e) {
            logger.error("Error calling unlockWalletFolder", e);
            throw new RuntimeException("Error calling unlockWalletFolder", e);
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