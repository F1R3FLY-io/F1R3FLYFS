package io.f1r3fly.fs.struct;


import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import fr.acinq.secp256k1.Hex;
import io.f1r3fly.fs.examples.ConfigStorage;
import io.f1r3fly.fs.examples.F1r3flyFS;
import io.f1r3fly.fs.examples.datatransformer.AESCipher;
import io.f1r3fly.fs.examples.storage.errors.NoDataByPath;
import io.f1r3fly.fs.examples.storage.errors.PathIsNotADirectory;
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
import org.testcontainers.containers.Network;
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
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class F1r3flyFSTest {
    private static final int GRPC_PORT = 40402;
    private static final int PROTOCOL_PORT = 40400;
    private static final int DISCOVERY_PORT = 40404;
    private static final String MAX_BLOCK_LIMIT = "1000";
    private static final int MAX_MESSAGE_SIZE = 1024 * 1024 * 1024;  // ~1G
    private static final Duration STARTUP_TIMEOUT = Duration.ofMinutes(2);
    private static final String validatorPrivateKey = "f9854c5199bc86237206c75b25c6aeca024dccc0f55df3a553131111fd25dd85";
    private static final String clientPrivateKey = "a8cf01d889cc6ef3119ecbd57301036a52c41ae6e44964e098cb2aefa4598954";
    private static final String clientWallet = "11112ZM9yrfaTrzCCbKjPbxBncjNCkMFsPqtcLFvhBf4Kqx6rpir2w";
    private static final Path MOUNT_POINT = new File("/tmp/f1r3flyfs/").toPath();
    private static final File MOUNT_POINT_FILE = MOUNT_POINT.toFile();


    public static final DockerImageName F1R3FLY_IMAGE = DockerImageName.parse(
//        "f1r3flyindustries/f1r3fly-rust-node:latest"
        "ghcr.io/f1r3fly-io/rnode:latest"
    );

    private static GenericContainer<?> f1r3flyBoot;
    private static String f1r3flyBootAddress;
    private static GenericContainer<?> f1r3flyObserver;
    private static Network network;

    private static final Logger log = (Logger) LoggerFactory.getLogger(F1r3flyFSTest.class);
    private static final Slf4jLogConsumer logConsumer = new Slf4jLogConsumer(log);
    private static final ListAppender<ILoggingEvent> listAppender = new ListAppender<>();

    private static F1r3flyFS f1r3flyFS;
    private static F1r3flyApi f1R3FlyApi;

    @BeforeEach
    void mount() throws InterruptedException {
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

        //commented b/c save the java heap memory
//        f1r3flyBoot.followOutput(logConsumer);
//        f1r3flyObserver.followOutput(logConsumer);

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

    private static void recreateDirectories() {
        Utils.cleanDataDirectory("data", Arrays.asList("genesis", "node.certificate.pem", "node.key.pem"));

        // Ensure the observer data directory exists
        new File("data/observer").mkdirs();
    }

    private static void forceUmountAndCleanup() {
        try { // try to unmount
            MountUtils.umount(MOUNT_POINT);
            MOUNT_POINT.toFile().delete();
            f1r3flyFS.umount();
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    @AfterEach
    void cleanup() {
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

    // TESTS:

    @Test
    @Disabled
    void shouldDeployRhoFileAfterRename() throws IOException, NoDataByPath {
        testToRenameTxtToDeployableExtension("rho");
    }

    @Disabled
    @Test
    void shouldDeployMettaFileAfterRename() throws IOException, NoDataByPath {
        testToRenameTxtToDeployableExtension("metta");
    }

    @Test
    @Disabled
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
    @Disabled
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

    @Disabled
    @Test
    void shouldStoreMettaFileAndDeployIt() throws IOException, NoDataByPath {
        testToCreateDeployableFile("metta"); // TODO: pass the correct Metta code from this line
    }

    @Test
    @Disabled
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

        assertWrittenData(file, inputDataAsBinary ,true, "Read data should be equal to written data");

        remount();

        assertWrittenData(file, inputDataAsBinary, false, "Read data should be equal to written data after remount");
    }

    @Test
    void shouldCreateRenameGetDeleteFiles() throws IOException {
        long start = System.currentTimeMillis();

        File file = new File(MOUNT_POINT_FILE, "file.bin");

        assertFalse(file.exists(), "File should not exist");
        assertTrue(file.createNewFile(), "Failed to create test file");
        assertTrue(file.exists(), "File should exist");

        assertContainChilds(MOUNT_POINT_FILE, file);

        byte[] inputDataAsBinary = new byte[1024 * 1024]; // 1 MB
        new Random().nextBytes(inputDataAsBinary);
        Files.write(file.toPath(), inputDataAsBinary);
        log.info("Written data length: {}", inputDataAsBinary.length);

        assertWrittenData(file, inputDataAsBinary, true, "Read data should be equal to written data");

        File renamedFile = new File(file.getParent(), "renamed.txt");
        assertTrue(file.renameTo(renamedFile), "Failed to rename file");

        assertContainChilds(MOUNT_POINT_FILE, renamedFile);

        assertWrittenData(renamedFile, inputDataAsBinary, true, "Read data (from renamed file) should be equal to written data");

        String inputDataAsString = "a".repeat(1024);
        Files.writeString(renamedFile.toPath(), inputDataAsString, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING); // truncate and override
        assertWrittenData(renamedFile, inputDataAsString.getBytes(), true, "Read data (from renamed file) should be equal to written data");

        assertContainChilds(MOUNT_POINT_FILE, renamedFile); // it has to be the same folder after truncate and overide

        File dir = new File(MOUNT_POINT_FILE, "testDir");
        assertTrue(dir.mkdir(), "Failed to create test directory");

        File nestedFile = new File(dir, "nestedFile.txt");
        assertTrue(nestedFile.createNewFile(), "Failed to create another file");

        assertContainChilds(MOUNT_POINT_FILE, renamedFile, dir);
        assertContainChilds(dir, nestedFile);

        remount(); // umount and mount back

        // check if deployed data is correct:

        assertContainChilds(MOUNT_POINT_FILE, renamedFile, dir);
        assertContainChilds(dir, nestedFile);

        String readDataAfterRemount = Files.readString(renamedFile.toPath());
        assertEquals(inputDataAsString, readDataAfterRemount, "Read data should be equal to written data");

        assertTrue(renamedFile.delete(), "Failed to delete file");
        assertTrue(nestedFile.delete(), "Failed to delete file");
        assertTrue(dir.delete(), "Failed to delete file");

        assertContainChilds(MOUNT_POINT_FILE); // empty

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

        assertContainChilds(MOUNT_POINT_FILE); // empty

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

        assertContainChilds(MOUNT_POINT_FILE); // empty
    }

    // Utility methods:

    private static void waitOnBackgroundDeployments() {
        if (f1r3flyFS == null)
            throw new IllegalStateException("f1r3flyFS is not initialized");

        f1r3flyFS.waitOnBackgroundThread();
    }

    private static void remount() {
        String mountName = f1r3flyFS.getMountName();
        f1r3flyFS.umount();
        forceUmountAndCleanup();
        try {
            f1r3flyFS.remount(mountName, MOUNT_POINT); // should pass: fetch the filesystem back
        } catch (PathIsNotADirectory | NoDataByPath e) {
            throw new RuntimeException(e);
        }
    }

    private static @NotNull byte[] getFileContentFromShardDirectly(File file) {

        try {
            RholangExpressionConstructor.ChannelData dirOrFile = getChanelData(file);

            assertTrue(dirOrFile.isFile(), "Chanel data should be a file");
            assertNotNull(dirOrFile.firstChunk(), "Chanel data should contain firstChunk field");
            assertNotNull(dirOrFile.otherChunks(), "Chanel data should contain otherChunks field");

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            outputStream.write(dirOrFile.firstChunk());

            Integer[] sortedKeys = dirOrFile.otherChunks().keySet().stream().sorted().toArray(Integer[]::new);
            for (Integer key : sortedKeys) {
                String subChannel = dirOrFile.otherChunks().get(key);
                List<RhoTypes.Par> data = f1R3FlyApi.findDataByName(subChannel);
                byte[] chunk = RholangExpressionConstructor.parseBytes(data);
                outputStream.write(chunk);
            }

            return outputStream.toByteArray();
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    private static @NotNull Set<String> getFolderChildrenFromShardDirectly(File file) {
        RholangExpressionConstructor.ChannelData dirOrFile = getChanelData(file);

        assertTrue(dirOrFile.isDir(), "Chanel data should be a directory");

        return dirOrFile.children();
    }

    private static @NotNull RholangExpressionConstructor.ChannelData getChanelData(File file) {
        // reading data from shard directly:
        // 1. Get a data from the file. File is a chanel at specific block
        // Reducing the path. Fuse changes the path, so we need to change it too:
        // - the REAL path   is /tmp/f1r3flyfs/test.txt
        // - the FUSE's path is /test.txt
        File fusePath = new File(file.getAbsolutePath().replace(MOUNT_POINT_FILE.getAbsolutePath(), "")); // /tmp/f1r3flyfs/test.txt -> /test.txt

        String fileNameAtShard = f1r3flyFS.prependMountName(fusePath.getPath());

        // wait on background deployments
        waitOnBackgroundDeployments();

        List<RhoTypes.Par> fileData = null;
        try {
            fileData = f1R3FlyApi.findDataByName(fileNameAtShard);
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
        List<RhoTypes.Par> pars = f1R3FlyApi.findDataByName(newRhoChanel);
        RhoTypes.Par first = pars.get(0);
        assertEquals(first.getExprsList().get(0).getGInt(), chanelValue, "Deployed data should be equal to written data");
    }

    private static void assertWrittenData(File file, byte[] inputDataAsBinary, boolean assertDataFromShard, String message) throws IOException {
        byte[] readDataAsBinary = Files.readAllBytes(file.toPath());
        assertArrayEquals(inputDataAsBinary, readDataAsBinary, message);
        if (assertDataFromShard) {
            assertFileContentFromShard(inputDataAsBinary, file);
        }
    }

    private static void assertFileContentFromShard(byte[] expectedData, File file) {
        byte[] readData = getFileContentFromShardDirectly(file);
        assertArrayEquals(expectedData, readData, "Read data should be equal to written data");
    }

    private static void assertContainChilds(File dir, File... expectedChilds) {
        File[] childs = dir.listFiles();

        File[] localFiles;

        if (dir == MOUNT_POINT_FILE) {
            File tokensDirectory = new File(MOUNT_POINT_FILE, ".tokens");
            // expected + tokensDirectory
            localFiles = Stream.concat(Arrays.stream(expectedChilds), Stream.of(tokensDirectory)).toArray(File[]::new);
        } else {
            localFiles = expectedChilds;
        }


        assertNotNull(childs, "Can't get list of files in %s".formatted(dir.getAbsolutePath()));
        assertEquals(localFiles.length, childs.length, "Should be only %s file(s) but found %s in %s".formatted(Arrays.toString(localFiles), Arrays.toString(childs), dir.getAbsolutePath()));

        for (File expectedChild : localFiles) {
            assertTrue(
                Arrays.stream(childs).anyMatch(file -> file.getName().equals(expectedChild.getName())),
                "Expected file %s not found in list of childs (%s)".formatted(expectedChild, Arrays.toString(childs))
            );
        }

        // and check the deployed data:

        Set<String> children = getFolderChildrenFromShardDirectly(dir);
        assertEquals(expectedChilds.length, children.size(), "Should be only %s file(s) in %s".formatted(expectedChilds.length, dir.getAbsolutePath()));

        for (File expectedChild : expectedChilds) {
            assertTrue(
                children.contains(expectedChild.getName()),
                "Expected file %s not found in list of childs (%s)".formatted(expectedChild, children)
            );
        }
    }

    private static void assertDirIsEmpty(File dir) {
        File[] files = Objects.requireNonNull(dir.listFiles());
        assertEquals(0, files.length, "Dir %s should be empty, but %s got".formatted(dir, Arrays.toString(files)));

        Set<String> folderChildrenFromShardDirectly = getFolderChildrenFromShardDirectly(dir);
        assertEquals(0, folderChildrenFromShardDirectly.size(), "Dir %s should be empty, but %s got".formatted(dir, folderChildrenFromShardDirectly));
    }

    private static void testIsNotEncrypted(File notEncrypted, String fileContent) throws IOException {
        String readData2 = Files.readString(notEncrypted.toPath());
        assertEquals(fileContent, readData2, "Read data should be equal to written data");

        String decodedFileData2 = new String(getFileContentFromShardDirectly(notEncrypted));
        assertEquals(fileContent, decodedFileData2, "Decoded data should be equal to the original data");
    }

    private static void testIsEncrypted(File encrypted, String expectedFileData) throws IOException, NoDataByPath {
        String readData = Files.readString(encrypted.toPath());
        assertEquals(expectedFileData, readData, "Read data should be equal to written data");

        String decodedFileData = new String(getFileContentFromShardDirectly(encrypted));
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

        List<RhoTypes.Par> pars = f1R3FlyApi.findDataByName(newRhoChanel);
        RhoTypes.Par first = pars.get(0);
        assertEquals(first.getExprsList().get(0).getGInt(), chanelValue, "Deployed data should be equal to written data");
    }

}
