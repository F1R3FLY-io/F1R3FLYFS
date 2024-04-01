package coop.f1r3fly.fs.struct;


import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Time;
import java.util.ArrayList;
import java.util.Arrays;

import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;

import casper.CasperMessage;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import coop.f1r3fly.fs.ErrorCodes;
import coop.f1r3fly.fs.FuseFillDir;
import coop.f1r3fly.fs.SuccessCodes;
import coop.f1r3fly.fs.examples.F1r3flyDeployer;
import jnr.constants.platform.OpenFlags;
import jnr.ffi.Memory;
import jnr.ffi.Pointer;
import jnr.ffi.Runtime;
import org.junit.Ignore;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.shaded.org.apache.commons.io.FileUtils;
import org.testcontainers.utility.DockerImageName;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import fr.acinq.secp256k1.Hex;

import coop.f1r3fly.fs.examples.F1r3flyFS;

import ch.qos.logback.classic.Logger;
import org.slf4j.LoggerFactory;

import static jnr.constants.platform.OpenFlags.O_RDONLY;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

@Testcontainers
public class F1r3flyFSTest {
    private static final int GRPC_PORT = 40402;
    private static final Duration STARTUP_TIMEOUT = Duration.ofMinutes(1);
    private static final String validatorPrivateKey = "f9854c5199bc86237206c75b25c6aeca024dccc0f55df3a553131111fd25dd85";
    private static final String clientPrivateKey = "a8cf01d889cc6ef3119ecbd57301036a52c41ae6e44964e098cb2aefa4598954";
    private static final String MOUNT_POINT = "/tmp/f1r3flyfs/" + clientPrivateKey;

    public static final DockerImageName F1R3FLY_IMAGE = DockerImageName.parse("ghcr.io/f1r3fly-io/rnode:latest");

    private static GenericContainer<?> f1r3fly;

    private static final Logger log = (Logger) LoggerFactory.getLogger(F1r3flyFSTest.class);
    private static final Slf4jLogConsumer logConsumer = new Slf4jLogConsumer(log);
    private static final ListAppender<ILoggingEvent> listAppender = new ListAppender<>();

    @Mock
    private F1r3flyDeployer mockDeployer;

    @InjectMocks
    private static F1r3flyFS f1r3flyFS;


    @BeforeEach
    void setUp() throws InterruptedException {
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

        F1r3flyDeployer deployer = new F1r3flyDeployer(Hex.decode(clientPrivateKey), f1r3fly.getHost(), f1r3fly.getMappedPort(GRPC_PORT));
        f1r3flyFS = new F1r3flyFS(deployer);
        f1r3flyFS.mount(Paths.get(MOUNT_POINT), false);

        //init mocks
//        MockitoAnnotations.openMocks(this); // Initialize mocks for an instance method
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
//        assertTrue(Files.exists(newFilePath), "File was not successfully renamed.");
//        assertFalse(Files.exists(testFilePath), "Original file still exists after rename.");
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
//
//    @Test
//    @Ignore
//    void shouldRemoveDirectorySuccessfully() throws IOException {
//        Path tempDir = Files.createTempDirectory("data/testDir");
//
//        int result = f1r3flyFS.rmdir(tempDir.toString());
//
//        assertEquals(0, result, "rmdir operation should return success code.");
//        assertFalse(Files.exists(tempDir), "Directory should be removed.");
//
//        // Cleanup is not needed here as the directory should already be removed
//    }
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
//        assertFalse(Files.exists(tempFile), "File should not exist after unlink.");
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
//        // Assuming ErrorCodes.EEXIST() returns the correct error code for "directory exists"
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


}
