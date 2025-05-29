package io.f1r3fly.fs.struct;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import fr.acinq.secp256k1.Hex;
import io.f1r3fly.fs.examples.ConfigStorage;
import io.f1r3fly.fs.examples.F1r3flyFS;
import io.f1r3fly.fs.examples.datatransformer.AESCipher;
import io.f1r3fly.fs.examples.storage.grcp.F1r3flyApi;
import io.f1r3fly.fs.utils.MountUtils;
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
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;

@Testcontainers
public class F1r3flyFSTestFixture {
    protected static final int GRPC_PORT = 40402;
    protected static final int PROTOCOL_PORT = 40400;
    protected static final int DISCOVERY_PORT = 40404;
    protected static final String MAX_BLOCK_LIMIT = "1000";
    protected static final int MAX_MESSAGE_SIZE = 1024 * 1024 * 1024;  // ~1G
    protected static final Duration STARTUP_TIMEOUT = Duration.ofMinutes(2);
    protected static final String validatorPrivateKey = "f9854c5199bc86237206c75b25c6aeca024dccc0f55df3a553131111fd25dd85";
    protected static final String clientPrivateKey = "a8cf01d889cc6ef3119ecbd57301036a52c41ae6e44964e098cb2aefa4598954";
    protected static final String clientWallet = "11112ZM9yrfaTrzCCbKjPbxBncjNCkMFsPqtcLFvhBf4Kqx6rpir2w";
    protected static final Path MOUNT_POINT = new File("/tmp/f1r3flyfs/").toPath();
    protected static final File MOUNT_POINT_FILE = MOUNT_POINT.toFile();

    public static final DockerImageName F1R3FLY_IMAGE = DockerImageName.parse(
        "ghcr.io/f1r3fly-io/rnode:latest"
    );

    protected static GenericContainer<?> f1r3flyBoot;
    protected static String f1r3flyBootAddress;
    protected static GenericContainer<?> f1r3flyObserver;
    protected static Network network;

    protected static final Logger log = (Logger) LoggerFactory.getLogger(F1r3flyFSTestFixture.class);
    protected static final Slf4jLogConsumer logConsumer = new Slf4jLogConsumer(log);
    protected static final ListAppender<ILoggingEvent> listAppender = new ListAppender<>();

    protected static F1r3flyFS f1r3flyFS;
    protected static F1r3flyApi f1R3FlyApi;

    @BeforeEach
    void setupContainers() throws InterruptedException {
        recreateDirectories();

        listAppender.start();
        log.addAppender(listAppender);

        ConfigStorage.setPrivateKey(Hex.decode(clientPrivateKey));
        ConfigStorage.setRevAddress(clientWallet);

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

        // Waits on the node initialization
        // Fresh start could take ~10 seconds
        Thread.sleep(30 * 1000);

        new File("/tmp/cipher.key").delete(); // remove key file if exists

        AESCipher.init("/tmp/cipher.key"); // file doesn't exist, so new key will be generated there
        f1R3FlyApi = new F1r3flyApi(Hex.decode(clientPrivateKey), 
                                   "localhost", f1r3flyBoot.getMappedPort(GRPC_PORT), 
                                   "localhost", f1r3flyObserver.getMappedPort(GRPC_PORT));
        f1r3flyFS = new F1r3flyFS(f1R3FlyApi);

        forceUmountAndCleanup(); // cleanup before mount
        f1r3flyFS.mount(MOUNT_POINT);
    }

    @AfterEach
    void tearDownContainers() {
        try {
            if (f1r3flyFS != null) {
                forceUmountAndCleanup();
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
            f1r3flyFS.umount();
            MountUtils.umount(MOUNT_POINT);
            MOUNT_POINT.toFile().delete();
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    protected static void waitOnBackgroundDeployments() {
        if (f1r3flyFS == null)
            throw new IllegalStateException("f1r3flyFS is not initialized");

        f1r3flyFS.waitOnBackgroundThread();
    }

    protected static void remount() {
        String mountName = f1r3flyFS.getMountName();
        f1r3flyFS.umount();
        forceUmountAndCleanup();
        try {
            f1r3flyFS.remount(mountName, MOUNT_POINT); // should pass: fetch the filesystem back
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
} 