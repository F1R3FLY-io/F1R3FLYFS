package io.f1r3fly.fs.struct;


import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import fr.acinq.secp256k1.Hex;
import io.f1r3fly.fs.examples.Config;
import io.f1r3fly.fs.examples.F1r3flyFS;
import io.f1r3fly.fs.examples.datatransformer.AESCipher;
import io.f1r3fly.fs.examples.storage.background.DeployDispatcher;
import io.f1r3fly.fs.examples.storage.errors.NoDataByPath;
import io.f1r3fly.fs.examples.storage.errors.PathIsNotADirectory;
import io.f1r3fly.fs.examples.storage.grcp.client.F1r3flyApi;
import io.f1r3fly.fs.examples.storage.rholang.RholangExpressionConstructor;
import io.f1r3fly.fs.utils.MountUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import rhoapi.RhoTypes;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class F1r3flyFSTest {
    private static final int GRPC_SHARD_PORT = 40402;
    private static final int GRPC_CLIENT_PORT = 51111;
    private static final String GRPC_CLIENT_HOST = "host.docker.internal";
    private static final String MAX_BLOCK_LIMIT = "1000";
    private static final int MAX_MESSAGE_SIZE = 1024 * 1024 * 1024;  // ~1G
    private static final Duration STARTUP_TIMEOUT = Duration.ofMinutes(2);
    private static final String validatorPrivateKey = "f9854c5199bc86237206c75b25c6aeca024dccc0f55df3a553131111fd25dd85";
    private static final String clientPrivateKey = "a8cf01d889cc6ef3119ecbd57301036a52c41ae6e44964e098cb2aefa4598954";
    private static final Path MOUNT_POINT = new File("/tmp/f1r3flyfs/").toPath();
    private static final Path MOUNT_POINT2 = new File("/tmp/f1r3flyfs2/").toPath();
    private static final File MOUNT_POINT_FILE = MOUNT_POINT.toFile();
    private static final File MOUNT_POINT_FILE2 = MOUNT_POINT2.toFile();

    public static final DockerImageName F1R3FLY_IMAGE = DockerImageName.parse("ghcr.io/f1r3fly-io/rnode:latest");

    private static GenericContainer<?> f1r3fly;

    private static final Logger log = (Logger) LoggerFactory.getLogger(F1r3flyFSTest.class);
    private static final Slf4jLogConsumer logConsumer = new Slf4jLogConsumer(log);
    private static final ListAppender<ILoggingEvent> listAppender = new ListAppender<>();

    private static Config config1;
    private static F1r3flyApi f1r3flyAPI1;
    private static F1r3flyFS f1r3flyFS;

    private static Config config2;
    private static F1r3flyApi f1r3flyAPI2;
    private static F1r3flyFS f1r3flyFS2;

    @BeforeEach
    void mount() throws InterruptedException {
        Utils.cleanDataDirectory("data", Arrays.asList("genesis", "node.certificate.pem", "node.key.pem"));

        listAppender.start();
        log.addAppender(listAppender);

        f1r3fly = new GenericContainer<>(F1R3FLY_IMAGE)
            .withFileSystemBind("data/", "/var/lib/rnode/", BindMode.READ_WRITE)
            .withExposedPorts(GRPC_SHARD_PORT)
            .withCommand("run -s --no-upnp --allow-private-addresses"
                + " --api-max-blocks-limit " + MAX_BLOCK_LIMIT
                + " --api-grpc-max-recv-message-size " + MAX_MESSAGE_SIZE
                + " --synchrony-constraint-threshold=0.0 --validator-private-key " + validatorPrivateKey)
            .withEnv("JAVA_TOOL_OPTIONS", "-Xmx4g")
            .waitingFor(Wait.forListeningPorts(GRPC_SHARD_PORT))
            .withStartupTimeout(STARTUP_TIMEOUT);

        f1r3fly.start(); // Manually start the container

        //commented b/c save the java heap memory
        f1r3fly.followOutput(logConsumer);

        // Waits on the node initialization
        // Fresh start could take ~10 seconds
        Thread.sleep(10 * 1000);

        new File("/tmp/cipher.key").delete(); // remove key file if exists

        AESCipher.init("/tmp/cipher.key"); // file doesn't exist, so new key will be generated there
        f1r3flyAPI1 = new F1r3flyApi(Hex.decode(clientPrivateKey), "localhost", f1r3fly.getMappedPort(GRPC_SHARD_PORT));
        String clientAlias1 = "host.docker.internal:51111";
        config1 = new Config(GRPC_CLIENT_HOST, GRPC_CLIENT_PORT, null, MOUNT_POINT, new DeployDispatcher(f1r3flyAPI1, clientAlias1), f1r3flyAPI1);
        f1r3flyFS = new F1r3flyFS(config1);

        if (MOUNT_POINT_FILE.exists()) {
            forceUmountAndCleanup(MOUNT_POINT, f1r3flyFS);
        }
        if (MOUNT_POINT_FILE2.exists()) {
            forceUmountAndCleanup(MOUNT_POINT2, f1r3flyFS2);
        }

        f1r3flyFS.mount(MOUNT_POINT);

    }

    private static void forceUmountAndCleanup() {
        forceUmountAndCleanup(MOUNT_POINT, f1r3flyFS);
    }

    private static void forceUmountAndCleanup(Path mountPoint, F1r3flyFS f1r3flyFS) {
        try { // try to unmount
            MountUtils.umount(mountPoint);
            mountPoint.toFile().delete();
            if (f1r3flyFS != null) {
                f1r3flyFS.umount();
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    @AfterEach
    void cleanup() {

        if (f1r3flyFS != null || MOUNT_POINT.toFile().exists()) {
            log.info("forceUmountAndCleanup {}", MOUNT_POINT);
            forceUmountAndCleanup(MOUNT_POINT, f1r3flyFS);
        }
        if (f1r3flyFS2 != null || MOUNT_POINT2.toFile().exists()) {
            log.info("forceUmountAndCleanup {}", MOUNT_POINT2);
            forceUmountAndCleanup(MOUNT_POINT2, f1r3flyFS2);
        }

        if (f1r3fly != null) {
            f1r3fly.stop();
        }
        listAppender.stop();
    }

    // TESTS:

    @Test
    void shouldDeployRhoFileAfterRename() throws IOException, NoDataByPath {
        testToRenameTxtToDeployableExtension("rho");
    }

    @Disabled
    @Test
    void shouldDeployMettaFileAfterRename() throws IOException, NoDataByPath {
        testToRenameTxtToDeployableExtension("metta");
    }

    @Test
    void shouldEncryptOnSaveAndDecryptOnReadForEncryptedExtension() throws IOException, NoDataByPath {
        File encrypted = new File(MOUNT_POINT_FILE, "test.txt.encrypted");
        String fileContent = "Hello, world!";

        Files.writeString(encrypted.toPath(), fileContent, StandardCharsets.UTF_8);

        testIsEncrypted(MOUNT_POINT_FILE.getAbsolutePath(), encrypted, fileContent);

        File notEncrypted = new File(MOUNT_POINT_FILE, "test.txt");
        Files.writeString(notEncrypted.toPath(), fileContent, StandardCharsets.UTF_8);

        testIsNotEncrypted(MOUNT_POINT_FILE.getAbsolutePath(), notEncrypted, fileContent);
    }

    @Test
    void shouldEncryptOnChangingExtension() throws IOException, NoDataByPath {
        File encrypted = new File(MOUNT_POINT_FILE, "test.txt.encrypted");
        String fileContent = "Hello, world!";

        Files.writeString(encrypted.toPath(), fileContent, StandardCharsets.UTF_8);

        testIsEncrypted(MOUNT_POINT_FILE.getAbsolutePath(), encrypted, fileContent);
        assertContainChilds(MOUNT_POINT_FILE.getAbsolutePath(), MOUNT_POINT_FILE, encrypted);

        File notEncrypted = new File(MOUNT_POINT_FILE, "test.txt");
        assertTrue(encrypted.renameTo(notEncrypted), "Failed to rename file");

        testIsNotEncrypted(MOUNT_POINT_FILE.getAbsolutePath(), notEncrypted, fileContent);

        assertContainChilds(MOUNT_POINT_FILE.getAbsolutePath(), MOUNT_POINT_FILE, notEncrypted);

        assertTrue(notEncrypted.renameTo(encrypted), "Failed to rename file");
        testIsEncrypted(MOUNT_POINT_FILE.getAbsolutePath(), encrypted, fileContent);
    }

    @Disabled
    @Test
    void shouldStoreMettaFileAndDeployIt() throws IOException, NoDataByPath {
        testToCreateDeployableFile("metta"); // TODO: pass the correct Metta code from this line
    }

    @Test
    void shouldStoreRhoFileAndDeployIt() throws IOException, NoDataByPath {
        testToCreateDeployableFile("rho");
    }

    @Disabled
    @Test
    void shouldWriteAndReadLargeFile() throws IOException, NoDataByPath, PathIsNotADirectory {
        File file = new File(MOUNT_POINT_FILE, "file.bin");

        assertTrue(file.createNewFile(), "Failed to create test file");

        byte[] inputDataAsBinary = new byte[512 * 1024 * 1024]; // 512mb
        new Random().nextBytes(inputDataAsBinary);
        Files.write(file.toPath(), inputDataAsBinary);

        assertWrittenData(MOUNT_POINT_FILE.getAbsolutePath(), file, inputDataAsBinary, true, "Read data should be equal to written data");

        remount();

        assertWrittenData(MOUNT_POINT_FILE.getAbsolutePath(), file, inputDataAsBinary, false, "Read data should be equal to written data after remount");
    }

    @Test
    void shouldCreateRenameGetDeleteFiles() throws IOException {
        long start = System.currentTimeMillis();

        File file = new File(MOUNT_POINT_FILE, "file.bin");

        assertFalse(file.exists(), "File should not exist");
        assertTrue(file.createNewFile(), "Failed to create test file");
        assertTrue(file.exists(), "File should exist");

        assertContainChilds(MOUNT_POINT_FILE.getAbsolutePath(), MOUNT_POINT_FILE, file);

        byte[] inputDataAsBinary = new byte[1024 * 1024]; // 1 Kb
        new Random().nextBytes(inputDataAsBinary);
        Files.write(file.toPath(), inputDataAsBinary);
        log.info("Written data length: {}", inputDataAsBinary.length);

        assertWrittenData(MOUNT_POINT_FILE.getAbsolutePath(), file, inputDataAsBinary, true, "Read data should be equal to written data");

        File renamedFile = new File(file.getParent(), "renamed.txt");
        assertTrue(file.renameTo(renamedFile), "Failed to rename file");

        assertContainChilds(MOUNT_POINT_FILE.getAbsolutePath(), MOUNT_POINT_FILE, renamedFile);

        assertWrittenData(MOUNT_POINT_FILE.getAbsolutePath(), renamedFile, inputDataAsBinary, true, "Read data (from renamed file) should be equal to written data");

        String inputDataAsString = "a".repeat(1024 * 1024 * 6);
        Files.writeString(renamedFile.toPath(), inputDataAsString, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING); // truncate and override
        assertWrittenData(MOUNT_POINT_FILE.getAbsolutePath(), renamedFile, inputDataAsString.getBytes(), true, "Read data (from renamed file) should be equal to written data");

        assertContainChilds(MOUNT_POINT_FILE.getAbsolutePath(), MOUNT_POINT_FILE, renamedFile); // it has to be the same folder after truncate and overide

        File dir = new File(MOUNT_POINT_FILE, "testDir");
        assertTrue(dir.mkdir(), "Failed to create test directory");

        File nestedFile = new File(dir, "nestedFile.txt");
        assertTrue(nestedFile.createNewFile(), "Failed to create another file");

        assertContainChilds(MOUNT_POINT_FILE.getAbsolutePath(), MOUNT_POINT_FILE, renamedFile, dir);
        assertContainChilds(MOUNT_POINT_FILE.getAbsolutePath(), dir, nestedFile);

        remount(); // umount and mount back

        // check if deployed data is correct:

        assertContainChilds(MOUNT_POINT_FILE.getAbsolutePath(), MOUNT_POINT_FILE, renamedFile, dir);
        assertContainChilds(MOUNT_POINT_FILE.getAbsolutePath(), dir, nestedFile);

        String readDataAfterRemount = Files.readString(renamedFile.toPath());
        assertEquals(inputDataAsString, readDataAfterRemount, "Read data should be equal to written data");

        assertTrue(renamedFile.delete(), "Failed to delete file");
        assertTrue(nestedFile.delete(), "Failed to delete file");
        assertTrue(dir.delete(), "Failed to delete file");

        assertDirIsEmpty(MOUNT_POINT_FILE.getAbsolutePath(), MOUNT_POINT_FILE); // empty

        assertFalse(renamedFile.exists(), "File should not exist");
        assertFalse(dir.exists(), "Directory should not exist");
        assertFalse(nestedFile.exists(), "File should not exist");

        long end = System.currentTimeMillis();
        // in seconds
        System.out.println("Time taken: " + (end - start) / 1000);
    }

    @Test
    void shouldCreateRenameListDeleteDirectories() {
        File dir1 = new File(MOUNT_POINT_FILE, "testDir");

        long start = System.currentTimeMillis();

        assertFalse(dir1.exists(), "Directory should not exist");
        assertTrue(dir1.mkdir(), "Failed to create test directory");
        assertTrue(dir1.exists(), "Directory should exist");

        assertContainChilds(MOUNT_POINT_FILE.getAbsolutePath(), MOUNT_POINT_FILE, dir1);

        File renamedDir = new File(MOUNT_POINT_FILE, "renamedDir");
        assertTrue(dir1.renameTo(renamedDir), "Failed to rename directory");

        assertContainChilds(MOUNT_POINT_FILE.getAbsolutePath(), MOUNT_POINT_FILE, renamedDir);

        File nestedDir1 = new File(renamedDir, "nestedDir1");
        File nestedDir2 = new File(nestedDir1, "nestedDir2");
        assertTrue(nestedDir2.mkdirs(), "Failed to create nested directories");

        assertContainChilds(MOUNT_POINT_FILE.getAbsolutePath(), renamedDir, nestedDir1);
        assertContainChilds(MOUNT_POINT_FILE.getAbsolutePath(), nestedDir1, nestedDir2);

        assertFalse(renamedDir.delete(), "Failed to delete non-empty directory");

        assertTrue(nestedDir2.delete(), "Failed to delete nested directory");
        assertTrue(nestedDir1.delete(), "Failed to delete nested directory");
        assertTrue(renamedDir.delete(), "Failed to delete directory");

        assertDirIsEmpty(MOUNT_POINT_FILE.getAbsolutePath(), MOUNT_POINT_FILE);

        long end = System.currentTimeMillis();
        // in seconds
        System.out.println("Time taken: " + (end - start) / 1000);
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

        assertDirIsEmpty(MOUNT_POINT_FILE.getAbsolutePath(), MOUNT_POINT_FILE);
    }

    @Test
    void shouldSyncFileBetween2Clients() throws IOException, NoDataByPath, InterruptedException, PathIsNotADirectory {
        // seconds mount point of the same user
        f1r3flyAPI2 = new F1r3flyApi(Hex.decode(clientPrivateKey), "localhost", f1r3fly.getMappedPort(GRPC_SHARD_PORT));
        String sameMountName = f1r3flyFS.getMountName();
        String clientAlias2 = "host.docker.internal:51112";
        config2 = new Config(GRPC_CLIENT_HOST, 51112, sameMountName, MOUNT_POINT2, new DeployDispatcher(f1r3flyAPI2, clientAlias2), f1r3flyAPI2);
        F1r3flyFS f1r3flyFS2 = new F1r3flyFS(config2);
        try {
            File aFile = new File(MOUNT_POINT_FILE, "aFile.txt");
            // write data to the aFile
            String writtenData = "Hello, world!";
            Files.writeString(aFile.toPath(), writtenData, StandardCharsets.UTF_8);

            assertContainChilds(MOUNT_POINT_FILE.getAbsolutePath(), MOUNT_POINT_FILE, aFile);

            f1r3flyFS2.remount(MOUNT_POINT2);

            File bFile = new File(MOUNT_POINT_FILE, "bFile.txt");
            String writeData2 = "Hello, world! 2";
            Files.writeString(bFile.toPath(), writeData2, StandardCharsets.UTF_8);

            waiting();

            // check if the aFile is there
            File aFile2 = new File(MOUNT_POINT_FILE2, "aFile.txt");
            File bFile2 = new File(MOUNT_POINT_FILE2, "bFile.txt");
            assertContainChilds(MOUNT_POINT_FILE2.getAbsolutePath(), MOUNT_POINT_FILE2, aFile2, bFile2);
            // read the aFile
            String readData = Files.readString(aFile2.toPath());
            assertEquals(writtenData, readData, "Read data should be equal to written data");

            String readData2 = Files.readString(bFile2.toPath());
            assertEquals(writeData2, readData2, "Read data should be equal to written data");

            // delete aFile
            assertTrue(aFile.delete(), "Failed to delete aFile");

            waiting();

            assertContainChilds(MOUNT_POINT_FILE.getAbsolutePath(), MOUNT_POINT_FILE, bFile);
            assertContainChilds(MOUNT_POINT_FILE2.getAbsolutePath(), MOUNT_POINT_FILE2, bFile2);

            File renamedFile = new File(MOUNT_POINT_FILE, "renamedFile.txt");
            assertTrue(bFile.renameTo(renamedFile), "Failed to rename file");

            waiting();

            File renamedFile2 = new File(MOUNT_POINT_FILE2, "renamedFile.txt");

            assertContainChilds(MOUNT_POINT_FILE2.getAbsolutePath(), MOUNT_POINT_FILE2, renamedFile2);
        } finally {
            f1r3flyFS2.umount();
        }
    }

    private static void waiting() throws InterruptedException {
        waitOnBackgroundDeployments(f1r3flyFS);
        waitOnBackgroundDeployments(f1r3flyFS2);
    }

    @Test
    void shouldSyncFoldersBetween2Clients() throws IOException, NoDataByPath, InterruptedException, PathIsNotADirectory {
        // seconds mount point of the same user
        f1r3flyAPI2 = new F1r3flyApi(Hex.decode(clientPrivateKey), "localhost", f1r3fly.getMappedPort(GRPC_SHARD_PORT));
        String sameMountName = f1r3flyFS.getMountName();
        String clientAlias2 = "host.docker.internal:51112";
        config2 = new Config(GRPC_CLIENT_HOST, 51112, sameMountName, MOUNT_POINT2, new DeployDispatcher(f1r3flyAPI2, clientAlias2), f1r3flyAPI2);
        F1r3flyFS f1r3flyFS2 = new F1r3flyFS(config2);
        try {
            File subDirA = new File(MOUNT_POINT_FILE, "subDirA");
            assertTrue(subDirA.mkdir(), "Failed to create test dir");

            waitOnBackgroundDeployments(f1r3flyFS);

            f1r3flyFS2.remount(MOUNT_POINT2);

            File subDirA2 = new File(MOUNT_POINT_FILE2, "subDirA");
            assertContainChilds(MOUNT_POINT_FILE2.getAbsolutePath(), MOUNT_POINT_FILE2, subDirA2);

            File subDirB = new File(MOUNT_POINT_FILE, "subDirB");
            assertTrue(subDirB.mkdir(), "Failed to create test dir");
            assertContainChilds(MOUNT_POINT_FILE.getAbsolutePath(), MOUNT_POINT_FILE, subDirA, subDirB);

            waiting();

            File subDirB2 = new File(MOUNT_POINT_FILE2, "subDirB");
            assertContainChilds(MOUNT_POINT_FILE2.getAbsolutePath(), MOUNT_POINT_FILE2, subDirA2, subDirB2);

            // delete subDirA
            assertTrue(subDirA.delete(), "Failed to delete subDirA");
            assertContainChilds(MOUNT_POINT_FILE.getAbsolutePath(), MOUNT_POINT_FILE, subDirB);

            waiting();

            assertContainChilds(MOUNT_POINT_FILE2.getAbsolutePath(), MOUNT_POINT_FILE2, subDirB2);

            File nestedDirC1 = new File(subDirB, "nestedDirC1");
            File nestedDirC2 = new File(subDirB2, "nestedDirC1");

            assertFalse(nestedDirC1.exists(), "Nested directory should not exist");
            assertFalse(nestedDirC2.exists(), "Nested directory should not exist");
            assertTrue(nestedDirC1.mkdir(), "Failed to create nested directory");

            waiting();

            assertContainChilds(MOUNT_POINT_FILE2.getAbsolutePath(), subDirB2, nestedDirC2);

            File nestedDirD1 = new File(nestedDirC1, "nestedDirD1");
            File nestedDirD2 = new File(nestedDirC2, "nestedDirD1");

            assertFalse(nestedDirD1.exists(), "Nested directory should not exist");
            assertFalse(nestedDirD2.exists(), "Nested directory should not exist");

            assertDirIsEmpty(MOUNT_POINT_FILE.getAbsolutePath(), nestedDirC1);
            assertDirIsEmpty(MOUNT_POINT_FILE2.getAbsolutePath(), nestedDirC2);

            assertTrue(nestedDirD1.mkdir(), "Failed to create nested directory");

            waiting();

            assertContainChilds(MOUNT_POINT_FILE2.getAbsolutePath(), subDirB2, nestedDirC2); // not changed
            assertContainChilds(MOUNT_POINT_FILE2.getAbsolutePath(), nestedDirC2, nestedDirD2); // contain a new nested directory
        } finally {
            f1r3flyFS2.umount();
        }
    }

    // Utility methods:

    private static void waitOnBackgroundDeployments() {
        waitOnBackgroundDeployments(f1r3flyFS);
    }

    private static void waitOnBackgroundDeployments(F1r3flyFS f1r3flyFS) {
        if (f1r3flyFS != null) {
            f1r3flyFS.waitOnBackgroundThread();
        }
    }

    private static void remount() {
        f1r3flyFS.umount();
        forceUmountAndCleanup();
        try {
            f1r3flyFS.remount(MOUNT_POINT); // should pass: fetch the filesystem back
        } catch (PathIsNotADirectory | NoDataByPath | IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private static @NotNull byte[] getFileContentFromShardDirectly(String mountPrefix, File file) {

        try {
            RholangExpressionConstructor.ChannelData dirOrFile = getChanelData(mountPrefix, file);

            assertTrue(dirOrFile.isFile(), "Chanel data should be a file");
            assertNotNull(dirOrFile.firstChunk(), "Chanel data should contain firstChunk field");
            assertNotNull(dirOrFile.otherChunks(), "Chanel data should contain otherChunks field");

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            outputStream.write(dirOrFile.firstChunk());

            Integer[] sortedKeys = dirOrFile.otherChunks().keySet().stream().sorted().toArray(Integer[]::new);
            for (Integer key : sortedKeys) {
                String subChannel = dirOrFile.otherChunks().get(key);
                List<RhoTypes.Par> data = f1r3flyAPI1.findDataByName(subChannel);
                byte[] chunk = RholangExpressionConstructor.parseBytes(data);
                outputStream.write(chunk);
            }

            return outputStream.toByteArray();
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    private static @NotNull Set<String> getFolderChildrenFromShardDirectly(String mountPrefix, File file) {
        RholangExpressionConstructor.ChannelData dirOrFile = getChanelData(mountPrefix, file);

        assertTrue(dirOrFile.isDir(), "Chanel data should be a directory");

        return dirOrFile.children();
    }

    private static RholangExpressionConstructor.@NotNull ChannelData getChanelData(String prefix, File file) {
        // reading data from shard directly:
        // 1. Get a data from the file. File is a chanel at specific block
        // Reducing the path. Fuse changes the path, so we need to change it too:
        // - the REAL path   is /tmp/f1r3flyfs/test.txt
        // - the FUSE's path is /test.txt
        File fusePath = new File(file.getAbsolutePath().replace(prefix, "")); // /tmp/f1r3flyfs/test.txt -> /test.txt

        String fileNameAtShard = f1r3flyFS.prependMountName(fusePath.getPath());

        // wait on background deployments
        waitOnBackgroundDeployments();

        List<RhoTypes.Par> fileData = null;
        try {
            fileData = f1r3flyAPI1.findDataByName(fileNameAtShard);
        } catch (NoDataByPath e) {
            throw new RuntimeException(e);
        }

        assertFalse(fileData.isEmpty(), "Chanel data %s should be not empty".formatted(fileNameAtShard));

        // 2. Parse Chanel value as a map
        return RholangExpressionConstructor.parseChannelData(fileData);
    }

    private static void testToCreateDeployableFile(String extension) throws IOException, NoDataByPath {
        File file = new File(MOUNT_POINT_FILE, "test." + extension);

        // TODO: use Metta syntax if .metta extension
        String newRhoChanel = "public";
        int chanelValue = new Random().nextInt();
        String rhoCode = """
            @"%s"!(%s)
            """.formatted(newRhoChanel, chanelValue);

        Files.writeString(file.toPath(), rhoCode, StandardCharsets.UTF_8);

        waitOnBackgroundDeployments();
        List<RhoTypes.Par> pars = f1r3flyAPI1.findDataByName("public");
        RhoTypes.Par first = pars.get(0);
        assertEquals(first.getExprsList().get(0).getGInt(), chanelValue, "Deployed data should be equal to written data");
    }

    private static void assertWrittenData(String mountPrefix, File file, byte[] inputDataAsBinary, boolean assertDataFromShard, String message) throws IOException {
        byte[] readDataAsBinary = Files.readAllBytes(file.toPath());
        assertArrayEquals(inputDataAsBinary, readDataAsBinary, message);
        if (assertDataFromShard) {
            assertFileContentFromShard(mountPrefix, inputDataAsBinary, file);
        }
    }

    private static void assertFileContentFromShard(String mountPrefix, byte[] expectedData, File file) {
        byte[] readData = getFileContentFromShardDirectly(mountPrefix, file);
        assertArrayEquals(expectedData, readData, "Read data should be equal to written data");
    }

    private static void assertContainChilds(String mountPrefix, File dir, File... expectedChilds) {
        File[] childs = dir.listFiles();

        assertNotNull(childs, "Can't get list of files in %s".formatted(dir.getAbsolutePath()));
        assertEquals(expectedChilds.length, childs.length, "Expected %s in %s. Got %s".formatted(Arrays.toString(expectedChilds), dir.getAbsolutePath(), Arrays.toString(childs)));

        for (File expectedChild : expectedChilds) {
            assertTrue(
                Arrays.stream(childs).anyMatch(file -> file.getName().equals(expectedChild.getName())),
                "Expected file %s not found in list of childs (%s)".formatted(expectedChild, Arrays.toString(childs))
            );
        }

        // and check the deployed data:

        Set<String> children = getFolderChildrenFromShardDirectly(mountPrefix, dir);
        assertEquals(expectedChilds.length, children.size(), "Should be only %s file(s) in %s".formatted(expectedChilds.length, dir.getAbsolutePath()));

        for (File expectedChild : expectedChilds) {
            assertTrue(
                children.contains(expectedChild.getName()),
                "Expected file %s not found in list of childs (%s)".formatted(expectedChild, children)
            );
        }
    }

    private static void assertDirIsEmpty(String mountPrefix, File dir) {
        File[] files = Objects.requireNonNull(dir.listFiles());
        assertEquals(0, files.length, "Dir %s should be empty, but %s got".formatted(dir, Arrays.toString(files)));

        Set<String> folderChildrenFromShardDirectly = getFolderChildrenFromShardDirectly(mountPrefix, dir);
        assertEquals(0, folderChildrenFromShardDirectly.size(), "Dir %s should be empty, but %s got".formatted(dir, folderChildrenFromShardDirectly));
    }

    private static void testIsNotEncrypted(String mountPrefix, File notEncrypted, String fileContent) throws IOException {
        String readData2 = Files.readString(notEncrypted.toPath());
        assertEquals(fileContent, readData2, "Read data should be equal to written data");

        String decodedFileData2 = new String(getFileContentFromShardDirectly(mountPrefix, notEncrypted));
        assertEquals(fileContent, decodedFileData2, "Decoded data should be equal to the original data");
    }

    private static void testIsEncrypted(String mountPrefix, File encrypted, String expectedFileData) throws IOException, NoDataByPath {
        String readData = Files.readString(encrypted.toPath());
        assertEquals(expectedFileData, readData, "Read data should be equal to written data");

        String decodedFileData = new String(getFileContentFromShardDirectly(mountPrefix, encrypted));
        // Actual data is encrypted. It should be different from the original data
        assertNotEquals(expectedFileData, decodedFileData, "Decoded data should be different from the original data");
    }

    private static void testToRenameTxtToDeployableExtension(String newExtension) throws IOException, NoDataByPath {
        File file = new File(MOUNT_POINT_FILE, "test.text");

        String newRhoChanel = "public";
        int chanelValue = new Random().nextInt();
        String rhoCode = """
            @"%s"!(%s)
            """.formatted(newRhoChanel, chanelValue);

        Files.writeString(file.toPath(), rhoCode, StandardCharsets.UTF_8);

        assertTrue(file.renameTo(new File(file.getParent(), "test." + newExtension)), "Failed to rename file");

        waitOnBackgroundDeployments();

        List<RhoTypes.Par> pars = f1r3flyAPI1.findDataByName(newRhoChanel);
        RhoTypes.Par first = pars.get(0);
        assertEquals(first.getExprsList().get(0).getGInt(), chanelValue, "Deployed data should be equal to written data");
    }

}
