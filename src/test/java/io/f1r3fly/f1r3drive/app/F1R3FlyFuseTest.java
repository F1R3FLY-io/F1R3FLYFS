package io.f1r3fly.f1r3drive.app;

import io.f1r3fly.f1r3drive.errors.NoDataByPath;
import io.f1r3fly.f1r3drive.errors.PathIsNotADirectory;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class F1R3FlyFuseTest extends F1r3flyFSTestUtils {

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

        simulateUnlockWalletDirectoryAction(client1Wallet, client1PrivateKey);

        File file = new File(client1Directory, "file.bin");

        assertFalse(file.exists(), "File should not exist");
        assertTrue(file.createNewFile(), "Failed to create test file");
        assertTrue(file.exists(), "File should exist");

        assertContainChilds(client1Directory, file);

        byte[] inputDataAsBinary = new byte[1024 * 1024]; // 1 MB
        new Random().nextBytes(inputDataAsBinary);
        Files.write(file.toPath(), inputDataAsBinary);
        log.info("Written data length: {}", inputDataAsBinary.length);

        assertWrittenData(file, inputDataAsBinary, true, "Read data should be equal to written data");

        File renamedFile = new File(file.getParent(), "renamed.txt");
        assertTrue(file.renameTo(renamedFile), "Failed to rename file");

        assertContainChilds(client1Directory, renamedFile);

        assertWrittenData(renamedFile, inputDataAsBinary, true, "Read data (from renamed file) should be equal to written data");

        String inputDataAsString = "a".repeat(1024);
        Files.writeString(renamedFile.toPath(), inputDataAsString, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING); // truncate and override
        assertWrittenData(renamedFile, inputDataAsString.getBytes(), true, "Read data (from renamed file) should be equal to written data");

        assertContainChilds(client1Directory, renamedFile); // it has to be the same folder after truncate and overide

        File dir = new File(client1Directory, "testDir");
        assertTrue(dir.mkdir(), "Failed to create test directory");

        File nestedFile = new File(dir, "nestedFile.txt");
        assertTrue(nestedFile.createNewFile(), "Failed to create another file");

        assertContainChilds(client1Directory, renamedFile, dir);
        assertContainChilds(dir, nestedFile);

        remount(); // umount and mount back

        // check if deployed data is correct:

        assertContainChilds(client1Directory, renamedFile, dir);
        assertContainChilds(dir, nestedFile);

        String readDataAfterRemount = Files.readString(renamedFile.toPath());
        assertEquals(inputDataAsString, readDataAfterRemount, "Read data should be equal to written data");

        assertTrue(renamedFile.delete(), "Failed to delete file");
        assertTrue(nestedFile.delete(), "Failed to delete file");
        assertTrue(dir.delete(), "Failed to delete file");

        assertContainChilds(client1Directory); // empty

        assertFalse(renamedFile.exists(), "File should not exist");
        assertFalse(dir.exists(), "Directory should not exist");
        assertFalse(nestedFile.exists(), "File should not exist");

        long end = System.currentTimeMillis();
        // in seconds
        System.out.println("Time taken: " + (end - start) / 1000);
    }

    @Test
    void shouldCreateRenameListDeleteDirectories() {
        simulateUnlockWalletDirectoryAction(client1Wallet, client1PrivateKey);

        File dir1 = new File(client1Directory, "testDir");

        long start = System.currentTimeMillis();

        assertFalse(dir1.exists(), "Directory should not exist");
        assertTrue(dir1.mkdir(), "Failed to create test directory");
        assertTrue(dir1.exists(), "Directory should exist");

        assertContainChilds(client1Directory, dir1);

        File renamedDir = new File(client1Directory, "renamedDir");
        assertTrue(dir1.renameTo(renamedDir), "Failed to rename directory");

        assertContainChilds(client1Directory, renamedDir);

        File nestedDir1 = new File(renamedDir, "nestedDir1");
        File nestedDir2 = new File(nestedDir1, "nestedDir2");
        assertTrue(nestedDir2.mkdirs(), "Failed to create nested directories");

        assertContainChilds(renamedDir, nestedDir1);
        assertContainChilds(nestedDir1, nestedDir2);

        assertFalse(renamedDir.delete(), "Failed to delete non-empty directory");

        assertTrue(nestedDir2.delete(), "Failed to delete nested directory");
        assertTrue(nestedDir1.delete(), "Failed to delete nested directory");
        assertTrue(renamedDir.delete(), "Failed to delete directory");

        assertContainChilds(client1Directory); // empty

        long end = System.currentTimeMillis();
        // in seconds
        System.out.println("Time taken: " + (end - start) / 1000);
    }

    @Test
    void shouldHandleOperationsWithNotExistingFileAndDirectory() {
        simulateUnlockWalletDirectoryAction(client1Wallet, client1PrivateKey);

        File dir = new File(client1Directory, "testDir");

        assertFalse(dir.exists(), "Not existing directory should not exist");
        assertNull(dir.listFiles(), "Not existing directory should not have any files");
        assertFalse(dir.renameTo(new File(dir.getParent(), "abc")), "rename should return error");
        assertFalse(dir.delete(), "unlink should return error");

        File file = new File(client1Directory, "testFile.txt");

        assertFalse(file.exists(), "Not existing file should not exist");
        assertThrows(IOException.class, () -> Files.readAllBytes(file.toPath()), "read should return error");
        assertFalse(file.renameTo(new File(file.getParent(), "abc")), "rename should return error");
        assertFalse(file.delete(), "unlink should return error");

        assertContainChilds(client1Directory); // empty

        // test with client2
        // folder is locked, so it should not be visible
        assertContainChildsLocally(client2Directory); // empty
        
    }
}
