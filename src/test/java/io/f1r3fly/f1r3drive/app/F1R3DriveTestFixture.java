package io.f1r3fly.f1r3drive.app;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.f1r3fly.f1r3drive.encryption.AESCipher;
import io.f1r3fly.f1r3drive.blockchain.client.F1r3flyBlockchainClient;
import io.f1r3fly.f1r3drive.fuse.struct.Utils;
import io.f1r3fly.f1r3drive.fuse.utils.MountUtils;
import io.f1r3fly.f1r3drive.app.contextmenu.client.FinderSyncExtensionServiceClient;
import generic.FinderSyncExtensionServiceOuterClass;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;

@Testcontainers
public class F1R3DriveTestFixture {
    protected static final int GRPC_PORT = 40402;
    protected static final int PROTOCOL_PORT = 40400;
    protected static final int DISCOVERY_PORT = 40404;
    protected static final String MAX_BLOCK_LIMIT = "1000";
    protected static final int MAX_MESSAGE_SIZE = 1024 * 1024 * 1024;  // ~1G
    protected static final Duration STARTUP_TIMEOUT = Duration.ofMinutes(2);
    protected static final String validatorPrivateKey = "f9854c5199bc86237206c75b25c6aeca024dccc0f55df3a553131111fd25dd85";
    protected static final Path MOUNT_POINT = new File("/tmp/f1r3drive/").toPath();
    protected static final File MOUNT_POINT_FILE = MOUNT_POINT.toFile();

    protected static final String REV_WALLET_1 = "11112ZM9yrfaTrzCCbKjPbxBncjNCkMFsPqtcLFvhBf4Kqx6rpir2w";
    protected static final String PRIVATE_KEY_1 = "a8cf01d889cc6ef3119ecbd57301036a52c41ae6e44964e098cb2aefa4598954";
    protected static final File LOCKED_WALLET_DIR_1 = new File(MOUNT_POINT_FILE, "LOCKED-REMOTE-REV-" + REV_WALLET_1);
    protected static final File UNLOCKED_WALLET_DIR_1 = new File(MOUNT_POINT_FILE, REV_WALLET_1);

    protected static final String REV_WALLET_2 = "1111AtahZeefej4tvVR6ti9TJtv8yxLebT31SCEVDCKMNikBk5r3g";
    protected static final String PRIVATE_KEY_2 = "5f668a7ee96d944a4494cc947e4005e172d7ab3461ee5538f1f2a45a835e9657";
    protected static final File LOCKED_WALLET_DIR_2 = new File(MOUNT_POINT_FILE, "LOCKED-REMOTE-REV-" + REV_WALLET_2);
    protected static final File UNLOCKED_WALLET_DIR_2 = new File(MOUNT_POINT_FILE, REV_WALLET_2);

    public static final DockerImageName F1R3FLY_IMAGE = DockerImageName.parse(
        "ghcr.io/f1r3fly-io/rnode:latest"
    );

    protected static GenericContainer<?> f1r3flyBoot;
    protected static String f1r3flyBootAddress;
    protected static GenericContainer<?> f1r3flyObserver;
    protected static Network network;

    protected static final Logger log = (Logger) LoggerFactory.getLogger(F1R3DriveTestFixture.class);
    protected static final Slf4jLogConsumer logConsumer = new Slf4jLogConsumer(log);
    protected static final ListAppender<ILoggingEvent> listAppender = new ListAppender<>();

    protected static F1r3DriveFuse f1r3DriveFuse;
    protected static F1r3flyBlockchainClient f1R3FlyBlockchainClient;

    @BeforeEach
    void setupContainers() throws InterruptedException {
        recreateDirectories();

        listAppender.start();
        log.addAppender(listAppender);

        // Create a network for containers to communicate
        network = Network.newNetwork();

        String bootAlias = "f1r3fly-boot";
        String observerAlias = "f1r3fly-observer";
        f1r3flyBoot = new GenericContainer<>(F1R3FLY_IMAGE)
            .withFileSystemBind("data/", "/var/lib/rnode/", BindMode.READ_WRITE)
            .withExposedPorts(GRPC_PORT, PROTOCOL_PORT, DISCOVERY_PORT)
            .withCommand("run -s --no-upnp --allow-private-addresses"
                + " --host " + bootAlias
                + " --api-max-blocks-limit " + MAX_BLOCK_LIMIT
                + " --api-grpc-max-recv-message-size " + MAX_MESSAGE_SIZE
                + " --synchrony-constraint-threshold=0.0 --validator-private-key " + validatorPrivateKey)
            .withEnv("JAVA_TOOL_OPTIONS", "-Xmx2g")
            .waitingFor(Wait.forListeningPorts(GRPC_PORT))
            .withNetwork(network)
            .withNetworkAliases(bootAlias)
            .withStartupTimeout(STARTUP_TIMEOUT);

        f1r3flyBoot.start(); // Manually start the container

        // Use container network alias for container-to-container communication
        f1r3flyBootAddress = "rnode://17e4e1fb1540554e72dbf91abe4647b85d8bd655@" + bootAlias + "?protocol=" + PROTOCOL_PORT + "&discovery=" + DISCOVERY_PORT;

        log.info("Using bootstrap address: {}", f1r3flyBootAddress);

        f1r3flyObserver = new GenericContainer<>(F1R3FLY_IMAGE)
            .withFileSystemBind("data/observer/", "/var/lib/rnode/", BindMode.READ_WRITE)
            .withExposedPorts(GRPC_PORT)
            .withCommand("run -b " + f1r3flyBootAddress + " --allow-private-addresses --no-upnp" +
                " --host " + observerAlias +
                " --approve-duration 10seconds --approve-interval 10seconds" +
                " --fork-choice-check-if-stale-interval 30seconds --fork-choice-stale-threshold 30seconds")
            .withEnv("JAVA_TOOL_OPTIONS", "-Xmx2g")
            .waitingFor(Wait.forListeningPorts(GRPC_PORT))
            .withNetwork(network)
            .withNetworkAliases(observerAlias)
            .withStartupTimeout(STARTUP_TIMEOUT);

        log.info("Starting observer with bootstrap address: {}", f1r3flyBootAddress);
        f1r3flyObserver.start();

        // commented b/c save the java heap memory
        // f1r3flyBoot.followOutput(logConsumer);

        // Wait for both containers' GRPC ports to be available
        waitForPortToOpen("localhost", f1r3flyBoot.getMappedPort(GRPC_PORT), STARTUP_TIMEOUT);
        waitForPortToOpen("localhost", f1r3flyObserver.getMappedPort(GRPC_PORT), STARTUP_TIMEOUT);

        new File("/tmp/cipher.key").delete(); // remove key file if exists

        AESCipher.init("/tmp/cipher.key"); // file doesn't exist, so new key will be generated there
        f1R3FlyBlockchainClient = new F1r3flyBlockchainClient(
            "localhost", f1r3flyBoot.getMappedPort(GRPC_PORT),
            "localhost", f1r3flyObserver.getMappedPort(GRPC_PORT));
        f1r3DriveFuse = new F1r3DriveFuse(f1R3FlyBlockchainClient);

        forceUmountAndCleanup(); // cleanup before mount

        // Add delay before mounting to ensure previous test cleanup is complete
        Thread.sleep(1000);

        f1r3DriveFuse.mount(MOUNT_POINT);

        // Add delay after mounting to ensure mount is stable
        Thread.sleep(1000);
    }

    @AfterEach
    void tearDownContainers() {
        try {
            if (f1r3DriveFuse != null) {
                forceUmountAndCleanup();
                // Add delay after cleanup to ensure complete unmount
                Thread.sleep(2000);
            }
        } catch (Throwable e) {
            e.printStackTrace();
            // ignore
        }
        if (f1r3flyBoot != null) {
            f1r3flyBoot.stop();
            f1r3flyBoot.close();
        }
        if (f1r3flyObserver != null) {
            f1r3flyObserver.stop();
            f1r3flyObserver.close();
        }

        if (network != null) {
            network.close();
        }

        recreateDirectories();

        listAppender.stop();
    }

    protected static void recreateDirectories() {
        Utils.cleanDataDirectory("data", Arrays.asList("genesis", "node.certificate.pem", "node.key.pem"));

        // Ensure the observer data directory exists
        new File("data/observer").mkdirs();
    }

    protected static void forceUmountAndCleanup() {
        try { // try to unmount
            if (f1r3DriveFuse != null) {
                f1r3DriveFuse.umount();
                Thread.sleep(1000); // Wait for unmount to complete
            }

            // Force unmount using system commands
            try {
                MountUtils.umount(MOUNT_POINT);
            } catch (Exception e) {
                // Ignore errors from force unmount
                log.debug("Force unmount failed (expected if not mounted): {}", e.getMessage());
            }

            // Clean up mount point directory more carefully
            File mountPointFile = MOUNT_POINT.toFile();
            if (mountPointFile.exists()) {
                // If directory exists and is not empty, try to clean it first
                if (mountPointFile.isDirectory()) {
                    File[] children = mountPointFile.listFiles();
                    if (children != null) {
                        for (File child : children) {
                            try {
                                if (child.isDirectory()) {
                                    // Recursively delete subdirectories
                                    deleteDirectoryRecursively(child);
                                } else {
                                    child.delete();
                                }
                            } catch (Exception e) {
                                log.warn("Failed to delete child file/directory: {}", child.getAbsolutePath(), e);
                            }
                        }
                    }
                }
                // Now try to delete the mount point directory itself
                if (!mountPointFile.delete()) {
                    log.warn("Failed to delete mount point directory: {}", mountPointFile.getAbsolutePath());
                }
            }

            // Ensure mount point directory is recreated clean
            if (!mountPointFile.mkdirs()) {
                // mkdirs() returns false if directory already exists, so check if it exists
                if (!mountPointFile.exists()) {
                    log.error("Failed to create mount point directory: {}", mountPointFile.getAbsolutePath());
                    throw new RuntimeException("Failed to create mount point directory");
                } else {
                    log.debug("Mount point directory already exists: {}", mountPointFile.getAbsolutePath());
                }
            } else {
                log.debug("Created mount point directory: {}", mountPointFile.getAbsolutePath());
            }

        } catch (Throwable e) {
            log.error("Error during forceUmountAndCleanup", e);
        }
    }

    private static void deleteDirectoryRecursively(File directory) {
        if (!directory.exists()) {
            return;
        }

        File[] children = directory.listFiles();
        if (children != null) {
            for (File child : children) {
                if (child.isDirectory()) {
                    deleteDirectoryRecursively(child);
                } else {
                    child.delete();
                }
            }
        }
        directory.delete();
    }

    protected static void waitOnBackgroundDeployments() {
        if (f1r3DriveFuse == null)
            throw new IllegalStateException("f1r3drive is not initialized");

        f1r3DriveFuse.waitOnBackgroundThread();
    }

    protected static void remount() {
        // String mountName = f1r3DriveFuse.getMountName();
        // f1r3DriveFuse.umount();
        // forceUmountAndCleanup();
        // try {
        //     f1r3DriveFuse.mount(MOUNT_POINT); // should pass: fetch the filesystem back
        // } catch (Exception e) {
        //     throw new RuntimeException(e);
        // }

        //TODO: do nothing for now
    }

    protected static void simulateUnlockWalletDirectoryAction(String revAddress, String privateKey) throws FinderSyncExtensionServiceClient.WalletUnlockException {
        try (FinderSyncExtensionServiceClient client = new FinderSyncExtensionServiceClient("localhost", 54000)) {
            client.unlockWalletDirectory(revAddress, privateKey);
        }
    }

    protected static void simulateChangeTokenAction(String tokenPath) throws FinderSyncExtensionServiceClient.ActionSubmissionException {
        try (FinderSyncExtensionServiceClient client = new FinderSyncExtensionServiceClient("localhost", 54000)) {
            client.submitAction(FinderSyncExtensionServiceOuterClass.MenuActionType.CHANGE, tokenPath);
        }
    }

    private static void waitForPortToOpen(String host, int port, Duration timeout) throws InterruptedException {
        long startTime = System.currentTimeMillis();
        long timeoutMillis = timeout.toMillis();

        log.info("Waiting for port {}:{} to become available...", host, port);

        while (System.currentTimeMillis() - startTime < timeoutMillis) {
            try (Socket socket = new Socket()) {
                socket.connect(new java.net.InetSocketAddress(host, port), 1000);
                log.info("Port {}:{} is now available", host, port);
                return;
            } catch (IOException e) {
                // Port not yet available, continue waiting
                Thread.sleep(1000);
            }
        }

        throw new RuntimeException("Timeout waiting for port " + host + ":" + port + " to become available");
    }
} 