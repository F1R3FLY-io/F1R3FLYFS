package io.f1r3fly.fs.struct;


import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import fr.acinq.secp256k1.Hex;
import io.f1r3fly.fs.FuseException;
import io.f1r3fly.fs.examples.F1r3flyFS;
import io.f1r3fly.fs.examples.datatransformer.AESCipher;
import io.f1r3fly.fs.examples.storage.errors.NoDataByPath;
import io.f1r3fly.fs.examples.storage.grcp.F1r3flyApi;
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
    private static final int GRPC_PORT = 40402;
    private static final String MAX_BLOCK_LIMIT = "2"; // FIXME: THIS DOESN'T WORK
    private static final int MAX_MESSAGE_SIZE = 1024 * 1024 * 1024;  // ~1G
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
    private static F1r3flyApi f1R3FlyApi;

    @BeforeEach
    void mount() throws InterruptedException {
        Utils.cleanDataDirectory("data", Arrays.asList("genesis", "node.certificate.pem", "node.key.pem"));

        listAppender.start();
        log.addAppender(listAppender);

        f1r3fly = new GenericContainer<>(F1R3FLY_IMAGE)
            .withFileSystemBind("data/", "/var/lib/rnode/", BindMode.READ_WRITE)
            .withExposedPorts(GRPC_PORT)
            .withCommand("run -s --no-upnp --allow-private-addresses"
                + " --api-max-blocks-limit " + MAX_BLOCK_LIMIT
                + " --api-grpc-max-recv-message-size " + MAX_MESSAGE_SIZE
                + " --synchrony-constraint-threshold=0.0 --validator-private-key " + validatorPrivateKey)
            .waitingFor(Wait.forListeningPorts(GRPC_PORT))
            .withStartupTimeout(STARTUP_TIMEOUT);


        f1r3fly.start(); // Manually start the container

        f1r3fly.followOutput(logConsumer);

        // Waits on the node initialization
        // Fresh start could take ~10 seconds
        Thread.sleep(10 * 1000);

        new File("/tmp/cipher.key").delete(); // remove key file if exists

        AESCipher aesCipher = new AESCipher("/tmp/cipher.key"); // file doesn't exist, so new key will be generated there
        f1R3FlyApi = new F1r3flyApi(Hex.decode(clientPrivateKey), "localhost", f1r3fly.getMappedPort(GRPC_PORT));
        f1r3flyFS = new F1r3flyFS(f1R3FlyApi, aesCipher);


        try {
            f1r3flyFS.mount(MOUNT_POINT);
        } catch (FuseException e) {
            // could fail if the mount point is already mounted from previous run

            e.printStackTrace();

            try { // try to unmount
                MountUtils.umount(MOUNT_POINT);
                MOUNT_POINT.toFile().delete();
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
    void shouldDeployRhoFileAfterRename() throws IOException {
        testToRenameTxtToDeployableExtension("rho");
    }

    @Disabled
    @Test
    void shouldDeployMettaFileAfterRename() throws IOException {
        testToRenameTxtToDeployableExtension("metta");
    }

    private static void testToRenameTxtToDeployableExtension(String newExtension) throws IOException {
        File file = new File(MOUNT_POINT_FILE, "test.text");

        String newRhoChanel = "public";
        String chanelValue = "{}";
        String rhoCode = """
            @"%s"!(%s)
            """.formatted(newRhoChanel, chanelValue);

        Files.writeString(file.toPath(), rhoCode, StandardCharsets.UTF_8);

        File renamedFile = new File(MOUNT_POINT_FILE, file.getName() + "." + newExtension);

        File blockhash = new File(MOUNT_POINT_FILE, renamedFile.getName() + ".blockhash");
        assertFalse(blockhash.exists(), "File " + blockhash.getName() + " should not exist");

        assertTrue(file.renameTo(renamedFile), "Failed to rename file");
        assertTrue(blockhash.exists(), "File " + blockhash.getName() + " should exist now");

        assertContainChilds(MOUNT_POINT_FILE, renamedFile, blockhash);

        assertTrue(renamedFile.renameTo(file), "Failed to rename file back");
        assertContainChilds(MOUNT_POINT_FILE, file, blockhash); // blockhash should be still there
    }

    @Test
    void shouldEncryptOnSaveAndDecryptOnReadForEncryptedExtension() throws IOException, NoDataByPath {
        File encrypted = new File(MOUNT_POINT_FILE, "test.txt.encrypted");
        String fileContent = "Hello, world!";

        Files.writeString(encrypted.toPath(), fileContent, StandardCharsets.UTF_8);

        testIsEncrypted(encrypted, fileContent);

        File notEncrypted = new File(MOUNT_POINT_FILE, "test.txt");
        Files.writeString(notEncrypted.toPath(), fileContent, StandardCharsets.UTF_8);

        testIsNotEncrypted(notEncrypted, fileContent);
    }

    @Test
    void shouldEncryptOnChangingExtension() throws IOException, NoDataByPath {
        File encrypted = new File(MOUNT_POINT_FILE, "test.txt.encrypted");
        String fileContent = "Hello, world!";

        Files.writeString(encrypted.toPath(), fileContent, StandardCharsets.UTF_8);

        testIsEncrypted(encrypted, fileContent);

        File notEncrypted = new File(MOUNT_POINT_FILE, "test.txt");
        assertTrue(encrypted.renameTo(notEncrypted), "Failed to rename file");

        testIsNotEncrypted(notEncrypted, fileContent);

        assertContainChilds(MOUNT_POINT_FILE, notEncrypted);

        assertTrue(notEncrypted.renameTo(encrypted), "Failed to rename file");
        testIsEncrypted(encrypted, fileContent);
    }

    private static void testIsNotEncrypted(File notEncrypted, String fileContent) throws IOException, NoDataByPath {
        String readData2 = Files.readString(notEncrypted.toPath());
        assertEquals(fileContent, readData2, "Read data should be equal to written data");

        String decodedFileData2 = getFileContentFromShardDirectly(notEncrypted);
        assertEquals(fileContent, decodedFileData2, "Decoded data should be equal to the original data");
    }

    private static void testIsEncrypted(File encrypted, String expectedFileData) throws IOException, NoDataByPath {
        String readData = Files.readString(encrypted.toPath());
        assertEquals(expectedFileData, readData, "Read data should be equal to written data");

        String decodedFileData = getFileContentFromShardDirectly(encrypted);
        // Actual data is encrypted. It should be different from the original data
        assertNotEquals(expectedFileData, decodedFileData, "Decoded data should be different from the original data");
    }

    private static @NotNull String getFileContentFromShardDirectly(File file) throws NoDataByPath {
        // reading data from shard directly:
        // 1. Get an internal state. It's stored at a last block and inside "mountPath" chanel
        List<RhoTypes.Par> pars = f1R3FlyApi.getDataAtName(f1r3flyFS.getLastBlockHash(), f1r3flyFS.getMountName());
        assertFalse(pars.isEmpty(), "Internal state should contain at least one element");
        HashMap<String, String> state = RholangExpressionConstructor.parseEMapFromLastExpr(pars);

        // 2. Get a data from the file. File is a chanel at specific block
        // Reducing the path. Fuse changes the path, so we need to change it too:
        // - the REAL path   is /tmp/f1r3flyfs/test.txt
        // - the FUSE's path is /test.txt
        File fusePath = new File(file.getAbsolutePath().replace(MOUNT_POINT_FILE.getAbsolutePath(), "")); // /tmp/f1r3flyfs/test.txt -> /test.txt

        String fileNameAtShard = f1r3flyFS.prependMountName(fusePath.getAbsolutePath());
        String blockHashOfFile = state.get(fileNameAtShard);
        assertNotNull(blockHashOfFile, "The file blockhash should be defined at a state");
        List<RhoTypes.Par> fileData = f1R3FlyApi.getDataAtName(blockHashOfFile, fileNameAtShard);

        // 3. Chanel value is a map with fields: type, value, size. 'value' contains base64 encoded data
        HashMap<String, String> fileDataMap = RholangExpressionConstructor.parseEMapFromLastExpr(fileData);
        assertTrue(fileDataMap.containsKey("value"), "File data should contain 'value' field");
        String encodedFileData = fileDataMap.get("value");
        return new String(Base64.getDecoder().decode(encodedFileData), StandardCharsets.UTF_8);
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

    private static void testToCreateDeployableFile(String extension) throws IOException, NoDataByPath {
        File file = new File(MOUNT_POINT_FILE, "test." + extension);

        // TODO: use Metta syntax if .metta extension
        String newRhoChanel = "public";
        String chanelValue = "{\"a\": \"b\"}";
        String rhoCode = """
            @"%s"!(%s)
            """.formatted(newRhoChanel, chanelValue);

        Files.writeString(file.toPath(), rhoCode, StandardCharsets.UTF_8);

        File fileCreatedAfterExecution = new File(MOUNT_POINT_FILE, file.getName() + ".blockhash");
        assertTrue(fileCreatedAfterExecution.exists(), "File " + fileCreatedAfterExecution.getName() + " should exist");

        assertContainChilds(MOUNT_POINT_FILE, file, fileCreatedAfterExecution);

        String blockHashFromFile = Files.readString(fileCreatedAfterExecution.toPath());

        assertFalse(blockHashFromFile.isEmpty(), "Block hash should not be empty");
        List<RhoTypes.Par> result = f1R3FlyApi.getDataAtName(blockHashFromFile, newRhoChanel);
        HashMap<String, String> parsedEMapFromLastExpr = RholangExpressionConstructor.parseEMapFromLastExpr(result);

        assertTrue(parsedEMapFromLastExpr.containsKey("a"), "Result should contain key 'a'");
        assertEquals("b", parsedEMapFromLastExpr.get("a"), "Result should contain key 'a' with value 'b'");
    }

    @Test
    void shouldCreateRenameGetDeleteFiles() throws IOException {
        File file = new File(MOUNT_POINT_FILE, "file.bin");

        assertFalse(file.exists(), "File should not exist");
        assertTrue(file.createNewFile(), "Failed to create test file");
        assertTrue(file.exists(), "File should exist");

        byte[] inputDataAsBinary = new byte[15*1024*1024]; // 15 MB
        new Random().nextBytes(inputDataAsBinary);
        Files.write(file.toPath(), inputDataAsBinary);
        log.info("Written data length: {}", inputDataAsBinary.length);

        byte[] readDataAsBinary = Files.readAllBytes(file.toPath());
        log.info("Read data length: {}", readDataAsBinary.length);
        assertArrayEquals(inputDataAsBinary, readDataAsBinary, "Read data should be equal to written data");

        File renamedFile = new File(file.getParent(), "renamed.txt");
        assertTrue(file.renameTo(renamedFile), "Failed to rename file");

        assertContainChilds(MOUNT_POINT_FILE, renamedFile);

        byte[] inputDataAsBinary2 = Files.readAllBytes(renamedFile.toPath());
        assertArrayEquals(inputDataAsBinary, inputDataAsBinary2, "Read data (from renamed file) should be equal to written data");

        String inputDataAsString = "a".repeat(1024); // 1 Kb
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

        assertNotNull(childs, "Can't get list of files in %s".formatted(dir.getAbsolutePath()));
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
