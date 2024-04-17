package io.f1r3fly.fs.struct;


import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.ByteBuffer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.f1r3fly.fs.FuseFillDir;
import io.f1r3fly.fs.SuccessCodes;
import io.f1r3fly.fs.examples.storage.grcp.F1r3flyApi;
import jnr.ffi.Pointer;
import org.junit.jupiter.api.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import jnr.ffi.Runtime;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.utility.DockerImageName;

import fr.acinq.secp256k1.Hex;

import io.f1r3fly.fs.examples.F1r3flyFS;

import ch.qos.logback.classic.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class F1r3flyFSTest {
    private static final int GRPC_PORT = 40402;
    private static final Duration STARTUP_TIMEOUT = Duration.ofMinutes(1);
    private static final String validatorPrivateKey = "f9854c5199bc86237206c75b25c6aeca024dccc0f55df3a553131111fd25dd85";
    private static final String clientPrivateKey = "a8cf01d889cc6ef3119ecbd57301036a52c41ae6e44964e098cb2aefa4598954";
    private static final Path MOUNT_POINT = new File("/tmp/f1r3flyfs/").toPath();
    private static final File MOUNT_POINT_FILE = MOUNT_POINT.toFile();

    public static final DockerImageName F1R3FLY_IMAGE = DockerImageName.parse("ghcr.io/f1r3fly-io/rnode:latest");

    private static GenericContainer<?> f1r3fly;

    private static final Logger log = (Logger) LoggerFactory.getLogger(F1r3flyFSTest.class);
    private static final Slf4jLogConsumer logConsumer = new Slf4jLogConsumer(log);
    private static final ListAppender<ILoggingEvent> listAppender = new ListAppender<>();

    @Mock
    private F1r3flyApi mockDeployer;

    @InjectMocks
    private static F1r3flyFS f1r3flyFS;


    @BeforeAll
    static void setUp() throws InterruptedException {
        Utils.cleanDataDirectory("data", Arrays.asList("genesis", "node.certificate.pem", "node.key.pem"));

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

        F1r3flyApi f1R3FlyApi = new F1r3flyApi(Hex.decode(clientPrivateKey), "localhost", f1r3fly.getMappedPort(GRPC_PORT));
        f1r3flyFS = new F1r3flyFS(f1R3FlyApi);


        //init mocks
//        MockitoAnnotations.openMocks(this); // Initialize mocks for an instance method
    }

    @BeforeEach
    void mount() {
        f1r3flyFS.mount(MOUNT_POINT, false);
    }

    @AfterEach
    void tearDown() {
        if (!(f1r3flyFS == null)) f1r3flyFS.umount();
    }

//    @Test
//    void shouldRunF1r3fly() {
//        long walletLoadedMsg = listAppender.list.stream()
//            .filter(event -> event.getFormattedMessage().contains("Wallet loaded"))
//            .count();
//        long bondsLoadedMsg = listAppender.list.stream()
//            .filter(event -> event.getFormattedMessage().contains("Bond loaded"))
//            .count();
//        long preChargingAndDeployMsg = listAppender.list.stream()
//            .filter(event -> event.getFormattedMessage().contains("rho:id:o3zbe6j6hby9mqzm6tdn6bb1fe7irw5xauqtcxsugct66ojudfttmn"))
//            .count();
//
//        assertTrue(f1r3fly.isRunning());
//        assertEquals(1, walletLoadedMsg, "Wallet.txt not successfully downloaded");
//        assertEquals(1, bondsLoadedMsg, "Bonds.txt not successfully downloaded");
////        assertEquals(2, preChargingAndDeployMsg, "onchaing-volume.rho did not return uri exactly two times");
//
//        f1r3flyFS.mount(null, false); // mountPoint is unused yet, so mount anywhere
//        f1r3flyFS.mkdir("/tmp", 0);
//        f1r3flyFS.mkdir("/tmp/folder1", 0);
//        f1r3flyFS.mkdir("/tmp/folder2", 0);
//        f1r3flyFS.write("/tmp/abc", 0, null); // fileinfo is not used yet
//        f1r3flyFS.rmdir("/tmp/folder2");
//        f1r3flyFS.rmdir("/tmp");
//        f1r3flyFS.umount();
//    }
//
//
//
//    @Test
//    void shouldWriteAndReadBackSameContent() {
//        String path1 = "/file1.txt";
//        String content1 = "Hello, F1r3fly!";
//
//        String path2 = "/file2.txt";
//        String content2 = "Hello, World!";
//
//        writeFile(path1, content1);
//        writeFile(path2, content2);
//
//        readFile(path1, content1);
//        readFile(path2, content2);
//
//        String files = readdir("/");
//
//        assertEquals("file1.txt,file2.txt", files);
//    }
//
//    @Test
//    void shouldFailIfParentDirectoryDoesNotExist() {
//        String path = "/tmp/file"; // /tmp does not exist
//        String content = "Hello, F1r3fly!";
//
//        writeFile(path, content, -ErrorCodes.ENOENT());
//    }
//
//    @Test
//    void shouldReturnNonFoundIfFileDeleted() {
//        String dirName = "/tmp";
//        String fileName = "file.txt";
//        String filePath = dirName + "/" + fileName;
//
//        mkdir(dirName);
//        writeFile(filePath, "Hello, F1r3fly!");
//        String childs = readdir(dirName);
//        assertEquals(fileName, childs);
//
//        truncateFile(filePath);
//
//        readNotExistingFile(filePath, -ErrorCodes.ENOENT());
//    }
//
//    @Test
//    void shouldReturnCreatedDirectoryOnMount() {
//        mkdir("/tmp");
//        mkdir("/var");
//        mkdir("/lib");
//
//        String dirs = readdir("/");
//
//        assertEquals("tmp,var,lib", dirs);
//    }
//
//    @Test
//    @Ignore
//    void shouldRenameFileSuccessfully() throws IOException {
//        Path testDirPath = Files.createTempDirectory("data/testDir");
//        Path testFilePath = testDirPath.resolve("testFile.txt");
//        Files.createFile(testFilePath); // Create the file to rename
//
//        Path newFilePath = testDirPath.resolve("newName.txt");
//
//        int result = f1r3flyFS.rename(testFilePath.toString(), newFilePath.toString());
//
//        assertEquals(0, result, "Rename operation failed.");
//        assertTrue(Files.getType(newFilePath), "File was not successfully renamed.");
//        assertFalse(Files.getType(testFilePath), "Original file still getType after rename.");
//
//        // Cleanup
//        FileUtils.deleteDirectory(testDirPath.toFile());
//    }
//
//    @Test
//    @Ignore
//    void shouldFailToRenameNonExistentFile() throws IOException {
//        Path testDirPath = Files.createTempDirectory("data/testDir");
//        Path nonExistentFilePath = testDirPath.resolve("nonExistentFile.txt");
//        Path newFilePath = testDirPath.resolve("newName.txt");
//
//        int result = f1r3flyFS.rename(nonExistentFilePath.toString(), newFilePath.toString());
//
//        assertEquals(-ErrorCodes.ENOENT(), result, "Expected ENOENT error code for non-existent file.");
//
//        // Cleanup
//        FileUtils.deleteDirectory(testDirPath.toFile());
//    }
//
//
//    @Test
//    @Ignore
//    void shouldTruncateFileSuccessfully() throws IOException {
//        // Setup: Create a temporary file and write some content to it
//        Path tempFile = Files.createTempFile("data/testFile", ".txt");
//        String content = "Hello, World!";
//        Files.write(tempFile, content.getBytes());
//
//        long newLength = 5;
//        int result = f1r3flyFS.truncate(tempFile.toString(), newLength);
//
//        assertEquals(0, result, "Truncate operation should return success code.");
//        assertEquals(newLength, Files.size(tempFile), "File was not truncated to the expected length.");
//
//        // Cleanup: Delete the temporary file
//        Files.deleteIfExists(tempFile);
//    }
//
//    @Test
//    @Ignore
//    void shouldFailToTruncateNonExistentFile() {
//        String nonExistentFilePath = "nonExistentFile.txt";
//
//        int result = f1r3flyFS.truncate(nonExistentFilePath, 10);
//
//        // Assert: Check that the error code for a non-existent file is returned
//        assertEquals(-ErrorCodes.ENOENT(), result, "Expected ENOENT error code for non-existent file.");
//    }

    @Test
    void shouldCreateAndDeleteDirectorySuccessfully() {
        File subdir = new File(MOUNT_POINT_FILE, "testDir");

        assertMountDirIsEmpty();

        assertTrue(subdir.mkdir(), "Failed to create test directory");
        assertTrue(subdir.exists(), "Test directory should exist after creation");
        assertTrue(subdir.isDirectory(), "Test directory should be a directory");
        assertFalse(subdir.isFile(), "Test directory should be not file");

        assertContainChilds(MOUNT_POINT_FILE, subdir);

        assertTrue(subdir.delete(), "Failed to delete test directory");

        assertMountDirIsEmpty();
    }

    @Test
    void shouldCreateAndDeleteFileSuccessfully() throws IOException {
        File file = new File(MOUNT_POINT_FILE, "test.txt");

        assertMountDirIsEmpty();

        assertTrue(file.createNewFile(), "Failed to create test file");
        assertTrue(file.exists(), "Test file should exist after creation");
        assertTrue(file.isFile(), "Test file should be a file");
        assertFalse(file.isDirectory(), "Test file should be not a directory");

        assertContainChilds(MOUNT_POINT_FILE, file);

        assertTrue(file.delete(), "Failed to delete test file");

        assertMountDirIsEmpty();
    }

    @Test
    void shouldWriteAndReadFileSuccessfully() throws IOException {
        File file = new File(MOUNT_POINT_FILE, "test.txt");

        assertTrue(file.createNewFile(), "Failed to create test file");

        String inputData = "Hello, F1r3fly!";
        Files.write(file.toPath(), inputData.getBytes());
        String readData = new String(Files.readAllBytes(file.toPath()));

        assertEquals(inputData, readData, "Read data should be equal to written data");
    }

    private static void assertContainChilds(File dir, File... expectedChilds) {
        File[] childs = dir.listFiles();

        assertNotNull(childs, "Can't get list of files in mount point");
        assertEquals(expectedChilds.length, childs.length, "Should be only %s file(s) in %s".formatted(expectedChilds.length, dir.getAbsolutePath()));

        for(File expectedChild : expectedChilds) {
            assertTrue(
                Arrays.stream(childs).anyMatch(file -> file.getName().equals(expectedChild.getName())),
                "Expected file %s not found in %s".formatted(expectedChild, dir.getAbsolutePath())
            );
        }
    }

    private static void assertMountDirIsEmpty() {
        assertEquals(0, Objects.requireNonNull(MOUNT_POINT_FILE.listFiles()).length, "Mount point should be empty");
    }
//
//    @Test
//    @Ignore
//    void shouldDeleteExistingFile() throws IOException {
//        Path tempDir = Files.createTempDirectory("data/testDir");
//        Path tempFile = Files.createFile(tempDir.resolve("fileToDelete.txt"));
//
//        // TODO as far as I understand unlink in scope of file = file deleting, not sure
//        int result = f1r3flyFS.unlink(tempFile.toString());
//
//        assertEquals(SuccessCodes.OK, result, "unlink should return success code.");
//        assertFalse(Files.getType(tempFile), "File should not exist after unlink.");
//
//        // Cleanup
//        FileUtils.deleteDirectory(tempDir.toFile());
//    }
//
//    @Test
//    @Ignore
//    void shouldFailToDeleteNonExistentFile() throws IOException {
//        Path tempDir = Files.createTempDirectory("data/testDir");
//        Path nonExistentFile = tempDir.resolve("nonExistentFile.txt");
//
//        int result = f1r3flyFS.unlink(nonExistentFile.toString());
//
//        assertEquals(-ErrorCodes.ENOENT(), result, "Expected ENOENT error code for non-existent file.");
//
//        // Cleanup
//        FileUtils.deleteDirectory(tempDir.toFile());
//    }
//
//    @Test
//    @Ignore
//    void shouldCreateDirectorySuccessfully() throws IOException {
//        Path tempDir = Files.createTempDirectory("data/testDir");
//        Path newDirPath = tempDir.resolve("newDirectory");
//
//        int result = f1r3flyFS.mkdir(newDirPath.toString(), 0777); //The owner can read, write, and execute.
//
//        assertEquals(SuccessCodes.OK, result, "mkdir should return success code.");
//        assertTrue(Files.isDirectory(newDirPath), "Directory should exist after mkdir.");
//
//        // Cleanup
//        FileUtils.deleteDirectory(tempDir.toFile());
//    }
//
//    @Test
//    @Ignore
//    void shouldFailToCreateExistingDirectory() throws IOException {
//        Path tempDir = Files.createTempDirectory("data");
//
//        int result = f1r3flyFS.mkdir(tempDir.toString(), 0777); //The owner can read, write, and execute.
//
//        // Assuming ErrorCodes.EEXIST() returns the correct error code for "directory getType"
//        assertEquals(-ErrorCodes.EEXIST(), result, "Expected EEXIST error code for existing directory.");
//
//        // Cleanup is not explicitly needed as the directory was supposed to exist already
//    }
//
//    @Test
//    @Ignore
//    void getattrShouldSucceedForExistingFile() throws Exception {
//
//        Path tempFile = Files.createTempFile("data/testFile", ".txt");
//
//        FileStat stat = new FileStat(jnr.ffi.Runtime.getRuntime(f1r3flyFS));
//
//        int result = f1r3flyFS.getattr(tempFile.toString(), stat);
//
//        assertEquals(SuccessCodes.OK, result, "Expected success code for existing file.");
//        assertTrue(stat.st_size.intValue() > 0, "Expected file size to be greater than 0.");
//        Files.deleteIfExists(tempFile); // Cleanup
//    }
//
//    @Test
//    @Ignore
//    void getattrShouldFailForNonExistentFile() {
//        String nonExistentFilePath = "nonExistentFile.txt";
//
//        FileStat stat = new FileStat(jnr.ffi.Runtime.getRuntime(f1r3flyFS));
//
//        int result = f1r3flyFS.getattr(nonExistentFilePath, stat);
//
//        // Assert
//        assertEquals(-ErrorCodes.ENOENT(), result, "Expected ENOENT error code for non-existent file.");
//    }
//
//    @Test
//    @Ignore
//    void openShouldSucceedForExistingFile() throws Exception {
//        Path tempFile = Files.createTempFile("data/testOpen", ".txt");
//
//        FuseFileInfo fi = new FuseFileInfo(jnr.ffi.Runtime.getSystemRuntime());
//        fi.flags.set(OpenFlags.O_RDONLY.value()); // The flags for opening a file (read, write, append, etc.)
//
//
//        int result = f1r3flyFS.open(tempFile.toString(), fi);
//
//        assertEquals(SuccessCodes.OK, result, "Expected success code for opening existing file.");
//        Files.deleteIfExists(tempFile); // Cleanup
//    }
//
//    @Test
//    @Ignore
//    void openShouldFailForNonExistentFile() {
//        String nonExistentFilePath = "nonExistentFile.txt";
//
//        FuseFileInfo fi = new FuseFileInfo(jnr.ffi.Runtime.getSystemRuntime());
//
//        int result = f1r3flyFS.open(nonExistentFilePath, fi);
//
//        assertEquals(-ErrorCodes.ENOENT(), result, "Expected ENOENT error code for non-existent file.");
//    }
//
//    @Test
//    @Ignore
//    void readdirShouldListDirectoryContents() throws Exception {
//        Path tempDir = Files.createTempDirectory("data/testDir");
//        Files.createFile(tempDir.resolve("file1.txt"));
//        Files.createFile(tempDir.resolve("file2.txt"));
//
//        MockFuseFillDir mockFillDir = new MockFuseFillDir();
//        FuseFileInfo fi = new FuseFileInfo(jnr.ffi.Runtime.getSystemRuntime());
//
//        // Use Runtime's memory manager to allocate memory
//        jnr.ffi.Runtime runtime = jnr.ffi.Runtime.getSystemRuntime();
//        Pointer dummyBuf = runtime.getMemoryManager().allocate(1024);
//
//        try {
//            int result = f1r3flyFS.readdir(tempDir.toString(), dummyBuf, mockFillDir, 0, fi);
//
//            assertEquals(SuccessCodes.OK, result, "Expected success code for reading directory.");
//            assertTrue(mockFillDir.getEntries().contains("file1.txt"), "Directory listing should include file1.txt");
//            assertTrue(mockFillDir.getEntries().contains("file2.txt"), "Directory listing should include file2.txt");
//        } finally {
//            // Free the allocated memory if necessary
//            // Note: With JNR-FFI, explicit memory deallocation is often not needed as Java's garbage collector
//            // will eventually reclaim memory allocated for Java objects. However, if you're using off-heap memory
//            // or native resources directly, you need to ensure they are freed appropriately.
//        }
//
//        // Cleanup
//        Files.deleteIfExists(tempDir.resolve("file1.txt"));
//        Files.deleteIfExists(tempDir.resolve("file2.txt"));
//        Files.deleteIfExists(tempDir);
//    }
//
//    @Test
//    @Ignore
//    void readdirShouldFailForNonExistentDirectory() throws Exception {
//        String nonExistentDirPath = "data/nonExistentTestDir";
//
//        MockFuseFillDir mockFillDir = new MockFuseFillDir();
//        FuseFileInfo fi = new FuseFileInfo(jnr.ffi.Runtime.getSystemRuntime());
//
//        jnr.ffi.Runtime runtime = jnr.ffi.Runtime.getSystemRuntime();
//        Pointer dummyBuf = runtime.getMemoryManager().allocate(1024);
//
//        try {
//            int result = f1r3flyFS.readdir(nonExistentDirPath, dummyBuf, mockFillDir, 0, fi);
//
//            assertEquals(-ErrorCodes.ENOENT(), result, "Expected ENOENT error code for non-existent directory.");
//        } finally {
//            // No specific cleanup needed for memory allocated in this test case
//        }
//    }
//
//
//    @Test
//    @Ignore
//    void readShouldSuccessfullyReadFromFile() throws Exception {
//        Path tempFile = Files.createTempFile("data/testRead", ".txt");
//        String content = "Hello, F1r3fly!";
//        Files.write(tempFile, content.getBytes(StandardCharsets.UTF_8));
//
//        // Allocate a buffer to read into
//        jnr.ffi.Runtime runtime = jnr.ffi.Runtime.getSystemRuntime();
//        Pointer buf = runtime.getMemoryManager().allocate(content.length());
//
//        FuseFileInfo fi = new FuseFileInfo(runtime);
//
//        try {
//            int bytesRead = f1r3flyFS.read(tempFile.toString(), buf, content.length(), 0, fi);
//
//            assertEquals(content.length(), bytesRead, "Expected to read the exact number of bytes written.");
//
//            byte[] readBytes = new byte[bytesRead];
//            buf.get(0, readBytes, 0, bytesRead); // Copy data from native memory
//            String readContent = new String(readBytes, StandardCharsets.UTF_8);
//
//            assertEquals(content, readContent, "Read content does not match expected content.");
//
//        } finally {
//            // Cleanup
//            Files.deleteIfExists(tempFile);
//            // No need to manually free the buffer allocated by JNR-FFI's MemoryManager in this context
//        }
//    }
//
//    @Test
//    @Ignore
//    void readShouldFailForNonExistentFile() throws Exception {
//        String nonExistentFilePath = "data/nonExistentFile.txt";
//
//        jnr.ffi.Runtime runtime = jnr.ffi.Runtime.getSystemRuntime();
//        Pointer buf = runtime.getMemoryManager().allocate(1024);
//
//        FuseFileInfo fi = new FuseFileInfo(runtime);
//
//        try {
//            int result = f1r3flyFS.read(nonExistentFilePath, buf, 1024, 0, fi);
//
//            // Assert: Verify that the operation failed with the expected error code
//            assertEquals(-ErrorCodes.ENOENT(), result, "Expected ENOENT error code for non-existent file.");
//
//        } finally {
//            // No need to manually free the buffer allocated by JNR-FFI's MemoryManager in this context
//        }
//    }
//
//    @Test
//    @Ignore
//    void createShouldSuccessfullyCreateNewFile() throws Exception {
//        //TODO think how we can test general approach of rholang code which worked under void method...
//        String newFilePath = "data/testDir/newFile.txt";
//        FuseFileInfo fi = new FuseFileInfo(jnr.ffi.Runtime.getSystemRuntime());
//        long mode = 1; // Example mode
//
//        int result = f1r3flyFS.create(newFilePath, mode, fi); //error in rholang code
//        Mockito.verify(mockDeployer, Mockito.times(1)).signDeploy(any(CasperMessage.DeployDataProto.class));
//
//    }
//
//    @Test
//    @Ignore
//    void statfsShouldSucceedForValidPath() {
//        // This setup assumes a real or mocked filesystem where `/tmp` should be a valid path.
//        Statvfs stbuf = new Statvfs(jnr.ffi.Runtime.getSystemRuntime());
//        String validPath = "/tmp"; // Ensure this is a valid path in your testing environment.
//
//        int result = f1r3flyFS.statfs(validPath, stbuf);
//
//        assertEquals(SuccessCodes.OK, result, "statfs should succeed for a valid path.");
//
//        assertTrue(stbuf.f_blocks.longValue() > 0, "Expected non-zero blocks in filesystem.");
//        // Add more assertions as necessary to validate the populated fields.
//    }
//
//    @Test
//    @Ignore
//    void statfsShouldFailForNonExistentPath() {
//        Statvfs stbuf = new Statvfs(jnr.ffi.Runtime.getSystemRuntime());
//        String nonExistentPath = "/path/does/not/exist";
//
//        int result = f1r3flyFS.statfs(nonExistentPath, stbuf);
//
//        assertEquals(-ErrorCodes.ENOENT(), result, "statfs should fail for non-existent path.");
//    }
//
//    @Test
//    @Ignore
//    void writeShouldSucceedForValidPath() throws IOException {
//        // Setup: Create a temporary file and prepare data to write
//        Path tempFile = Files.createTempFile("data/testWrite", ".txt");
//        byte[] dataToWrite = "Hello, world!".getBytes();
//        jnr.ffi.Runtime runtime = jnr.ffi.Runtime.getSystemRuntime();
//        Pointer dummyBuf = runtime.getMemoryManager().allocate(1024);
//        FuseFileInfo fi = new FuseFileInfo(jnr.ffi.Runtime.getSystemRuntime());
//
//        // Act: Attempt to write data to the file
//        int bytesWritten = f1r3flyFS.write(tempFile.toString(), dummyBuf, dataToWrite.length, 0, fi);
//
//        // Assert: Verify successful write operation
//        assertEquals(dataToWrite.length, bytesWritten, "Expected to write the exact number of bytes.");
//
//        // Verify the file content
//        byte[] fileContent = Files.readAllBytes(tempFile);
//        assertArrayEquals(dataToWrite, fileContent, "File content does not match the data written.");
//
//        // Cleanup
//        Files.deleteIfExists(tempFile);
//    }
//
//
//    @Test
//    @Ignore
//    void writeShouldFailForNonExistentPath() {
//        String nonExistentPath = "/path/does/not/exist";
//        jnr.ffi.Runtime runtime = jnr.ffi.Runtime.getSystemRuntime();
//        Pointer dummyBuf = runtime.getMemoryManager().allocate(1024);
//        FuseFileInfo fi = new FuseFileInfo(jnr.ffi.Runtime.getSystemRuntime());
//
//        int result = f1r3flyFS.write(nonExistentPath, dummyBuf, 0, 0, fi);
//
//        assertEquals(-ErrorCodes.ENOENT(), result, "write should fail for non-existent path.");
//    }
//
//
//
//
//
//
//    class MockFuseFillDir implements FuseFillDir {
//        List<String> entries = new ArrayList<>();
//
//        @Override
//        public int apply(Pointer buf, ByteBuffer name, Pointer stbuf, long off) {
//            byte[] bytes = new byte[name.remaining()];
//            name.get(bytes);
//            String entryName = new String(bytes, StandardCharsets.UTF_8);
//            entries.add(entryName);
//            // Simulate adding entry successfully
//            return 0;
//        }
//
//        public List<String> getEntries() {
//            return entries;
//        }
//    }


    /**
     * Write a content by the path and check a result code
     */
    void writeFile(String path, String fileContent) {
        writeFile(path, fileContent, SuccessCodes.OK);
    }

    void writeFile(String path, String fileContent, int expectedResultCode) {
        byte[] bytes = fileContent.getBytes();
        ByteBuffer inputData = ByteBuffer.wrap(bytes);
        Pointer pointer = Pointer.wrap(Runtime.getSystemRuntime(), inputData);

        FuseFileInfo fuseFileInfo = null;
        int offset = 0;

        int writeExitOperationCode =
            f1r3flyFS.write(path, pointer, bytes.length, offset, fuseFileInfo);

        assertEquals(expectedResultCode, writeExitOperationCode);
    }

    /**
     * Read a content by the path and match it with an expected content
     */
    void readFile(String path, String expectedContent) {
        int expectedContentSize = expectedContent.getBytes().length;
        Pointer buffer = Pointer.wrap(Runtime.getSystemRuntime(), ByteBuffer.wrap(new byte[expectedContentSize]));

        int offset = 0;
        FuseFileInfo fuseFileInfo = null;

        int readExitOperationCode =
            f1r3flyFS.read(path, buffer, expectedContentSize, offset, fuseFileInfo);

        assertEquals(SuccessCodes.OK, readExitOperationCode);

        String actualContent = buffer.getString(offset);

        assertEquals(expectedContent, actualContent);
    }

    /**
     * Read a content by the path and match it with an expected content
     */
    void readNotExistingFile(String path, int expectedErrorCode) {
        int expectedContentSize = 0;
        Pointer buffer = Pointer.wrap(Runtime.getSystemRuntime(), ByteBuffer.wrap(new byte[0]));

        int offset = 0;
        FuseFileInfo fuseFileInfo = null;

        int readExitOperationCode =
            f1r3flyFS.read(path, buffer, expectedContentSize, offset, fuseFileInfo);

        assertEquals(expectedErrorCode, readExitOperationCode);
    }


    private static String readdir(String path) {
        Pointer buffer = Pointer.wrap(Runtime.getSystemRuntime(), ByteBuffer.wrap(new byte[1024 * 1024]));
        FuseFileInfo fuseFileInfo = null;
        FuseFillDir filter = null;

        int resultCode = f1r3flyFS.readdir(path, buffer, filter, 0, fuseFileInfo);

        assertEquals(SuccessCodes.OK, resultCode);

        return buffer.getString(0);
    }

    private void mkdir(String dirName) {
        int returnCode = f1r3flyFS.mkdir(dirName, 0);
        assertEquals(SuccessCodes.OK, returnCode);
    }

    private static void truncateFile(String path) {
        int result = f1r3flyFS.truncate(path, 0);
        assertEquals(SuccessCodes.OK, result);
    }

}
