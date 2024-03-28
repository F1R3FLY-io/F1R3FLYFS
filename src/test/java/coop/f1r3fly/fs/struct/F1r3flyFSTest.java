package coop.f1r3fly.fs.struct;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.apache.commons.io.FileUtils;
import coop.f1r3fly.fs.examples.F1r3flyDeployer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.shaded.org.apache.commons.io.filefilter.IOFileFilter;
import org.testcontainers.shaded.org.apache.commons.io.filefilter.TrueFileFilter;
import org.testcontainers.utility.DockerImageName;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.acinq.secp256k1.Hex;

import coop.f1r3fly.fs.examples.F1r3flyFS;

import ch.qos.logback.classic.Logger;
import org.slf4j.LoggerFactory;

@Testcontainers
public class F1r3flyFSTest {
    private static final int GRPC_PORT = 40402;
    private static final Duration STARTUP_TIMEOUT = Duration.ofMinutes(1);
    private static final String validatorPrivateKey = "f9854c5199bc86237206c75b25c6aeca024dccc0f55df3a553131111fd25dd85";
    private static final String clientPrivateKey = "a8cf01d889cc6ef3119ecbd57301036a52c41ae6e44964e098cb2aefa4598954";
    private static final String MOUNT_POINT = "/tmp/f1r3flyfs/" + clientPrivateKey;

    public static final DockerImageName F1R3FLY_IMAGE = DockerImageName.parse("ghcr.io/f1r3fly-io/rnode:latest");

    private static GenericContainer<?> f1r3fly;
    private static F1r3flyFS f1r3flyFS;

    private static final Logger log = (Logger) LoggerFactory.getLogger(F1r3flyFSTest.class);
    private static final Slf4jLogConsumer logConsumer = new Slf4jLogConsumer(log);
    private static final ListAppender<ILoggingEvent> listAppender = new ListAppender<>();

    @BeforeAll
    static void setUp() throws InterruptedException {
        cleanDataDirectory("data", Arrays.asList("genesis", "node.certificate.pem", "node.key.pem"));

        listAppender.start();
        log.addAppender(listAppender);

        f1r3fly = new GenericContainer<>(F1R3FLY_IMAGE)
            .withFileSystemBind("data/", "/var/lib/rnode/", BindMode.READ_WRITE)
            .withExposedPorts(GRPC_PORT)
            .withCommand("run -s --no-upnp --allow-private-addresses --synchrony-constraint-threshold=0.0 --validator-private-key " + validatorPrivateKey)
            .waitingFor(Wait.forHealthcheck())
            .withStartupTimeout(STARTUP_TIMEOUT);
        f1r3fly.start(); // Manually start the container

        f1r3fly.followOutput(logConsumer);

        // Waits on the node initialization
        // Fresh start could take ~10 seconds
        Thread.sleep(20 * 1000);

        F1r3flyDeployer deployer = new F1r3flyDeployer(Hex.decode(clientPrivateKey), f1r3fly.getHost(), f1r3fly.getMappedPort(GRPC_PORT));
        f1r3flyFS = new F1r3flyFS(deployer);
        f1r3flyFS.mount(Paths.get(MOUNT_POINT), false);
    }

    @AfterAll
    static void tearDown() {
        if (!(f1r3flyFS == null)) f1r3flyFS.umount();
    }

    @Test
    void shouldRunF1r3fly() {
        long walletLoadedMsg = listAppender.list.stream()
            .filter(event -> event.getFormattedMessage().contains("Wallet loaded"))
            .count();
        long bondsLoadedMsg = listAppender.list.stream()
            .filter(event -> event.getFormattedMessage().contains("Bond loaded"))
            .count();
        long preChargingAndDeployMsg = listAppender.list.stream()
            .filter(event -> event.getFormattedMessage().contains("rho:id:o3zbe6j6hby9mqzm6tdn6bb1fe7irw5xauqtcxsugct66ojudfttmn"))
            .count();

        assertTrue(f1r3fly.isRunning());
        assertEquals(1, walletLoadedMsg, "Wallet.txt not successfully downloaded");
        assertEquals(1, bondsLoadedMsg, "Bonds.txt not successfully downloaded");
        assertEquals(2, preChargingAndDeployMsg, "onchaing-volume.rho did not return uri exactly two times");

    }

//    @Test
//    @Ignore
//    void shouldWriteAndReadSameContent() {
//        String path = "/tmp/abc";
//        int mode = 0;
//
//        ByteBuffer inputData = ByteBuffer.wrap(new byte[]{1, 2, 3, 4});
//
//        Pointer content = Pointer.wrap(Runtime.getRuntime(f1r3flyFS), inputData);
//        int contentSize = 0;
//        int offset = 0;
//        FuseFileInfo inputFuseFileInto = FuseFileInfo.of(content);
//
//        // needed?
////      f1r3flyFS.mkdir(path, mode);
////      f1r3flyFS.create(path, mode, null);
//
//        int writeExitOperationCode = f1r3flyFS.write(path, content, contentSize, offset, inputFuseFileInto);
//
//        assertEquals(writeExitOperationCode, SuccessCodes.OK);
//
//        ByteBuffer outputBuffer = ByteBuffer.allocateDirect(contentSize);
//        Pointer buffer = Pointer.wrap(Runtime.getRuntime(f1r3flyFS), outputBuffer);
//        int readExitOperationCode = f1r3flyFS.read(path, buffer, contentSize, offset, inputFuseFileInto);
//
//        assertEquals(readExitOperationCode, SuccessCodes.OK);
//
//        assertEquals(inputData.compareTo(outputBuffer), 0); // zero if equals
//    }

    public static void cleanDataDirectory(String destination, List<String> excludeList) {
        try {
            // if test fails, try to cleanup the data folder of the node manually
            // cd data && rm -rf blockstorage dagstorage eval rspace casperbuffer deploystorage rnode.log && cd
            cleanDirectoryExcept(destination, excludeList);
            log.debug("Cleaned directory, except for specified exclusions.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void cleanDirectoryExcept(String directoryPath, List<String> excludeList) throws IOException {
        File directory = new File(directoryPath);
        Path dirPath = directory.toPath();
        File[] files = directory.listFiles();

        if (files != null) {
            for (File file : files) {
                Path filePath = file.toPath();
                String relativePath = dirPath.relativize(filePath).toString();

                if (!excludeList.contains(relativePath)) {
                    if (file.isDirectory()) {
                        FileUtils.deleteDirectory(file);
                    } else {
                        Files.deleteIfExists(file.toPath());
                    }
                }
            }
        }
    }


}
