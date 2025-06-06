package io.f1r3fly.f1r3drive.app;

import io.f1r3fly.f1r3drive.errors.NoDataByPath;
import io.f1r3fly.f1r3drive.filesystem.local.TokenDirectory;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class F1r3flyFSAssertions extends F1R3FlyFuseTestFixture {

    public static void assertWrittenData(File file, byte[] inputDataAsBinary, boolean assertDataFromShard,
                                         String message) throws IOException {
        byte[] readDataAsBinary = Files.readAllBytes(file.toPath());
        assertArrayEquals(inputDataAsBinary, readDataAsBinary, message);
        if (assertDataFromShard) {
            assertFileContentFromShard(inputDataAsBinary, file);
        }
    }

    public static void assertFileContentFromShard(byte[] expectedData, File file) {
        byte[] readData = F1r3flyFSHelpers.getFileContentFromShardDirectly(file);
        assertArrayEquals(expectedData, readData, "Read data should be equal to written data");
    }

    public static void assertContainChilds(File dir, File... expectedChilds) {
        assertContainChildsLocally(dir, expectedChilds);

        F1r3flyFSHelpers.waitOnBackgroundDeployments();

        // and check the deployed data:
        Set<String> children = F1r3flyFSHelpers.getFolderChildrenFromShardDirectly(dir);
        assertEquals(expectedChilds.length, children.size(),
            "Should be only %s file(s) in %s".formatted(expectedChilds.length, dir.getAbsolutePath()));
        for (File expectedChild : expectedChilds) {
            assertTrue(
                children.contains(expectedChild.getName()),
                "Expected file %s not found in list of childs (%s)".formatted(expectedChild, children));
        }
    }

    public static void assertContainTokenDirectory(File dir) {
        assertTrue(new File(dir, TokenDirectory.NAME).exists(), "%s directory should exist in %s. %s contains %s"
            .formatted(TokenDirectory.NAME, dir.getAbsolutePath(), dir.getName(), Arrays.toString(dir.listFiles())));
    }

    public static void assertContainChildsLocally(File dir, File... expectedChilds) {
        File[] childs = dir.listFiles();

        assertNotNull(childs, "Can't get list of files in %s".formatted(dir.getAbsolutePath()));

        if (F1r3flyFSHelpers.isWalletDirectory(dir)) {
            // remove token folder from childs
            childs = Arrays.stream(childs).filter(c -> !c.getName().equals(TokenDirectory.NAME)).toArray(File[]::new);
        }

        assertEquals(expectedChilds.length, childs.length, "Should be only %s file(s) but found %s in %s"
            .formatted(Arrays.toString(expectedChilds), Arrays.toString(childs), dir.getAbsolutePath()));

        for (File expectedChild : expectedChilds) {
            assertTrue(
                Arrays.stream(childs).anyMatch(file -> file.getName().equals(expectedChild.getName())),
                "Expected file %s not found in list of childs (%s)".formatted(expectedChild,
                    Arrays.toString(childs)));
        }
    }

    public static void assertDirIsEmptyLocally(File dir) {
        File[] files = dir.listFiles();
        assertNotNull(files, "Dir %s listFiles returned null".formatted(dir));
        assertEquals(0, files.length, "Dir %s should be empty, but %s got".formatted(dir, Arrays.toString(files)));
    }

    /**
     * Unlocks a wallet directory and asserts that it doesn't throw an exception
     */
    public static void assertUnlockWalletDirectory(String wallet, String privateKey) {
        assertDoesNotThrow(() -> simulateUnlockWalletDirectoryAction(wallet, privateKey));
    }

    /**
     * Creates a new file and asserts the creation lifecycle: not exists -> create -> exists
     */
    public static void assertCreateNewFile(File file) throws IOException {
        assertFalse(file.exists(), "File should not exist before creation");
        assertTrue(file.createNewFile(), "Failed to create test file");
        assertTrue(file.exists(), "File should exist after creation");
    }

    /**
     * Creates a new directory and asserts the creation lifecycle: not exists -> create -> exists
     */
    public static void assertCreateNewDirectory(File dir) {
        assertFalse(dir.exists(), "Directory should not exist before creation");
        assertTrue(dir.mkdir(), "Failed to create test directory");
        assertTrue(dir.exists(), "Directory should exist after creation");
    }

    /**
     * Renames a file and asserts the operation was successful
     */
    public static void assertRenameFile(File source, File target) {
        assertTrue(source.renameTo(target), "Failed to rename file from " + source.getName() + " to " + target.getName());
    }

    /**
     * Deletes a file and asserts it was successful and no longer exists
     */
    public static void assertDeleteFile(File file) {
        assertTrue(file.delete(), "Failed to delete file " + file.getName());
        assertFalse(file.exists(), "File should not exist after deletion");
    }

    /**
     * Deletes a directory and asserts it was successful and no longer exists
     */
    public static void assertDeleteDirectory(File dir) {
        assertTrue(dir.delete(), "Failed to delete directory " + dir.getName());
        assertFalse(dir.exists(), "Directory should not exist after deletion");
    }

    /**
     * Asserts that operations on non-existent files should fail
     */
    public static void assertNonExistentFileOperationsFail(File file) {
        assertFalse(file.exists(), "Not existing file should not exist");
        assertThrows(IOException.class, () -> Files.readAllBytes(file.toPath()), "read should return error");
        assertFalse(file.renameTo(new File(file.getParent(), "abc")), "rename should return error");
        assertFalse(file.delete(), "unlink should return error");
    }

    /**
     * Asserts that operations on non-existent directories should fail
     */
    public static void assertNonExistentDirectoryOperationsFail(File dir) {
        assertFalse(dir.exists(), "Not existing directory should not exist");
        assertNull(dir.listFiles(), "Not existing directory should not have any files");
        assertFalse(dir.renameTo(new File(dir.getParent(), "abc")), "rename should return error");
        assertFalse(dir.delete(), "unlink should return error");
    }

    /**
     * Validates token file properties: ends with .token, is file, read-only, operations fail
     */
    public static void assertTokenFileProperties(File tokenFile) {
        assertTrue(tokenFile.exists(), "Token file should exist");
        assertTrue(tokenFile.getName().endsWith(".token"), "Token file should end with .token");
        assertTrue(tokenFile.isFile(), "Token file should be a file");
        assertFalse(tokenFile.renameTo(new File(tokenFile.getParent(), "10000000.token")), "rename should return error");
        assertFalse(tokenFile.delete(), "unlink should return error");
        assertFalse(tokenFile.mkdir(), "mkdir should return error");
    }

    /**
     * Creates nested directories and validates the structure
     */
    public static void assertCreateNestedDirectories(File parentDir, String... dirNames) {
        File currentDir = parentDir;
        for (String dirName : dirNames) {
            File nextDir = new File(currentDir, dirName);
            assertTrue(nextDir.mkdirs(), "Failed to create nested directory " + dirName);
            currentDir = nextDir;
        }
    }

    /**
     * Writes binary data to file and validates it's written correctly
     */
    public static void assertWriteBinaryData(File file, byte[] data) throws IOException {
        Files.write(file.toPath(), data);
        assertWrittenData(file, data, true, "Read data should be equal to written data");
    }

    /**
     * Writes string data to file and validates it's written correctly
     */
    public static void assertWriteStringData(File file, String data) throws IOException {
        Files.writeString(file.toPath(), data, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
        assertWrittenData(file, data.getBytes(), true, "Read data should be equal to written data");
    }
} 