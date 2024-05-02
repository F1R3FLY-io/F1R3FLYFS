package io.f1r3fly.fs.struct;


import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import fr.acinq.secp256k1.Hex;
import io.f1r3fly.fs.FuseException;
import io.f1r3fly.fs.examples.F1r3flyFS;
import io.f1r3fly.fs.examples.storage.grcp.F1r3flyApi;
import io.f1r3fly.fs.utils.MountUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class F1r3flyFSTest {
    private static final int GRPC_PORT = 40402;
    private static final String MAX_BLOCK_LIMIT = "2"; // FIXME: THIS DOESN'T WORK
    private static final Duration STARTUP_TIMEOUT = Duration.ofMinutes(2);
    private static final String validatorPrivateKey = "f9854c5199bc86237206c75b25c6aeca024dccc0f55df3a553131111fd25dd85";
    private static final String clientPrivateKey = "a8cf01d889cc6ef3119ecbd57301036a52c41ae6e44964e098cb2aefa4598954";
    private static final Path MOUNT_POINT = new File("/tmp/f1r3flyfs/").toPath();
    private static final File MOUNT_POINT_FILE = MOUNT_POINT.toFile();

    public static final DockerImageName F1R3FLY_IMAGE = DockerImageName.parse("ghcr.io/f1r3fly-io/rnode:latest");

    private static GenericContainer<?> f1r3fly;

    private static final Logger log = (Logger) LoggerFactory.getLogger(F1r3flyFSTest.class);
    private static final Slf4jLogConsumer logConsumer = new Slf4jLogConsumer(log);
    private static final ListAppender<ILoggingEvent> listAppender = new ListAppender<>();

    private static F1r3flyFS f1r3flyFS;

    @BeforeEach
    void mount() throws InterruptedException {
        Utils.cleanDataDirectory("data", Arrays.asList("genesis", "node.certificate.pem", "node.key.pem"));

        listAppender.start();
        log.addAppender(listAppender);

        f1r3fly = new GenericContainer<>(F1R3FLY_IMAGE)
            .withFileSystemBind("data/", "/var/lib/rnode/", BindMode.READ_WRITE)
            .withExposedPorts(GRPC_PORT)
            .withCommand("run -s --no-upnp --allow-private-addresses --api-max-blocks-limit " + MAX_BLOCK_LIMIT + " --synchrony-constraint-threshold=0.0 --validator-private-key " + validatorPrivateKey)
            .waitingFor(Wait.forListeningPorts(GRPC_PORT))
            .withStartupTimeout(STARTUP_TIMEOUT);


        f1r3fly.start(); // Manually start the container

//        f1r3fly.followOutput(logConsumer);

        // Waits on the node initialization
        // Fresh start could take ~10 seconds
        Thread.sleep(10 * 1000);

        F1r3flyApi f1R3FlyApi = new F1r3flyApi(Hex.decode(clientPrivateKey), "localhost", f1r3fly.getMappedPort(GRPC_PORT));
        f1r3flyFS = new F1r3flyFS(f1R3FlyApi);


        try {
            f1r3flyFS.mount(MOUNT_POINT);
        } catch (FuseException e) {
            // could fail if the mount point is already mounted from previous run

            e.printStackTrace();

            try { // try to unmount
                MountUtils.umount(MOUNT_POINT);
                f1r3flyFS.umount();
            } catch (Throwable e2) {
                // ignore
            }

            f1r3flyFS.mount(MOUNT_POINT);
        }
    }

    @AfterEach
    void tearDown() {
        if (f1r3flyFS != null) {
            f1r3flyFS.umount();
            f1r3fly.stop();
            Utils.cleanDataDirectory("data", Arrays.asList("genesis", "node.certificate.pem", "node.key.pem"));
        }
    }

    @Test
    void shouldCreateRenameGetDeleteFiles() throws IOException {
        File file = new File(MOUNT_POINT_FILE, "file.bin");

        assertFalse(file.exists(), "File should not exist");
        assertTrue(file.createNewFile(), "Failed to create test file");
        assertTrue(file.exists(), "File should exist");

        byte[] inputDataAsBinary = new byte[1024 * 1024]; // 1 MB
        new Random().nextBytes(inputDataAsBinary);
        Files.write(file.toPath(), inputDataAsBinary);
        byte[] readDatAsBinary = Files.readAllBytes(file.toPath());
        assertArrayEquals(inputDataAsBinary, readDatAsBinary, "Read data should be equal to written data");

        File renamedFile = new File(file.getParent(), "renamed.txt");
        assertTrue(file.renameTo(renamedFile), "Failed to rename file");

        assertContainChilds(MOUNT_POINT_FILE, renamedFile);

        byte[] inputDataAsBinary2 = Files.readAllBytes(renamedFile.toPath());
        assertArrayEquals(inputDataAsBinary, inputDataAsBinary2, "Read data (from renamed file) should be equal to written data");

        String inputDataAsString = "a".repeat(2 * 1024 * 1024); // 2 MB
        Files.writeString(renamedFile.toPath(), inputDataAsString, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING); // truncate and override
        String readDataAsString = Files.readString(renamedFile.toPath());
        assertEquals(inputDataAsString, readDataAsString, "Read data should be equal to written data");

        assertContainChilds(MOUNT_POINT_FILE, renamedFile); // it has to be the same folder after truncate and overide

        File dir = new File(MOUNT_POINT_FILE, "testDir");
        assertTrue(dir.mkdir(), "Failed to create test directory");

        File nestedFile = new File(dir, "nestedFile.txt");
        assertTrue(nestedFile.createNewFile(), "Failed to create another file");

        assertContainChilds(MOUNT_POINT_FILE, renamedFile, dir);
        assertContainChilds(dir, nestedFile);

        assertTrue(renamedFile.delete(), "Failed to delete file");
        assertTrue(nestedFile.delete(), "Failed to delete file");
        assertTrue(dir.delete(), "Failed to delete file");

        assertDirIsEmpty(MOUNT_POINT_FILE); // empty

        assertFalse(renamedFile.exists(), "File should not exist");
        assertFalse(dir.exists(), "Directory should not exist");
        assertFalse(nestedFile.exists(), "File should not exist");
    }

    @Test
    void shouldCreateRenameListDeleteDirectories() {
        File dir1 = new File(MOUNT_POINT_FILE, "testDir");

        assertFalse(dir1.exists(), "Directory should not exist");
        assertTrue(dir1.mkdir(), "Failed to create test directory");
        assertTrue(dir1.exists(), "Directory should exist");

        assertContainChilds(MOUNT_POINT_FILE, dir1);

        File renamedDir = new File(MOUNT_POINT_FILE, "renamedDir");
        assertTrue(dir1.renameTo(renamedDir), "Failed to rename directory");

        assertContainChilds(MOUNT_POINT_FILE, renamedDir);

        File nestedDir1 = new File(renamedDir, "nestedDir1");
        File nestedDir2 = new File(nestedDir1, "nestedDir2");
        assertTrue(nestedDir2.mkdirs(), "Failed to create nested directories");

        assertContainChilds(renamedDir, nestedDir1);
        assertContainChilds(nestedDir1, nestedDir2);

        assertFalse(renamedDir.delete(), "Failed to delete non-empty directory");

        assertTrue(nestedDir2.delete(), "Failed to delete nested directory");
        assertTrue(nestedDir1.delete(), "Failed to delete nested directory");
        assertTrue(renamedDir.delete(), "Failed to delete directory");

        assertDirIsEmpty(MOUNT_POINT_FILE);
    }


    @Test
    void shouldHandleOperationsWithNotExistingFileAndDirectory() {
        File dir = new File(MOUNT_POINT_FILE, "testDir");

        assertFalse(dir.exists(), "Not existing directory should not exist");
        assertNull(dir.listFiles(), "Not existing directory should not have any files");
        assertFalse(dir.renameTo(new File(dir.getParent(), "abc")), "rename should return error");
        assertFalse(dir.delete(), "unlink should return error");

        File file = new File(MOUNT_POINT_FILE, "testFile.txt");

        assertFalse(file.exists(), "Not existing file should not exist");
        assertThrows(IOException.class, () -> Files.readAllBytes(file.toPath()), "read should return error");
        assertFalse(file.renameTo(new File(file.getParent(), "abc")), "rename should return error");
        assertFalse(file.delete(), "unlink should return error");

        assertDirIsEmpty(MOUNT_POINT_FILE);
    }

    private static void assertContainChilds(File dir, File... expectedChilds) {
        File[] childs = dir.listFiles();

        assertNotNull(childs, "Can't get list of files in mount point");
        assertEquals(expectedChilds.length, childs.length, "Should be only %s file(s) in %s".formatted(expectedChilds.length, dir.getAbsolutePath()));

        for (File expectedChild : expectedChilds) {
            assertTrue(
                Arrays.stream(childs).anyMatch(file -> file.getName().equals(expectedChild.getName())),
                "Expected file %s not found in %s".formatted(expectedChild, dir.getAbsolutePath())
            );
        }
    }

    private static void assertDirIsEmpty(File dir) {
        assertEquals(0, Objects.requireNonNull(dir.listFiles()).length, "Dir %s should be empty".formatted(dir));
    }

}
