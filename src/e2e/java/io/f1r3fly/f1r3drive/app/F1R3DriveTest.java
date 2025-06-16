package io.f1r3fly.f1r3drive.app;

import io.f1r3fly.f1r3drive.finderextensions.client.FinderSyncExtensionServiceClient.WalletUnlockException;
import io.f1r3fly.f1r3drive.errors.NoDataByPath;
import io.f1r3fly.f1r3drive.errors.PathIsNotADirectory;
import io.f1r3fly.f1r3drive.filesystem.local.TokenDirectory;
import io.f1r3fly.f1r3drive.filesystem.utils.PathUtils;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static io.f1r3fly.f1r3drive.app.F1r3DriveAssertions.*;
import static io.f1r3fly.f1r3drive.app.F1r3DriveTestHelpers.*;

class F1R3DriveTest extends F1R3DriveTestFixture {

    // TESTS:

    @Test
    @Disabled
    @DisplayName("Should deploy Rho file after renaming from txt extension")
    void shouldDeployRhoFileAfterRename() throws IOException, NoDataByPath {
        testToRenameTxtToDeployableExtension("rho");
    }

    @Disabled
    @Test
    @DisplayName("Should deploy Metta file after renaming from txt extension")
    void shouldDeployMettaFileAfterRename() throws IOException, NoDataByPath {
        testToRenameTxtToDeployableExtension("metta");
    }

    @Test
    @Disabled
    @DisplayName("Should encrypt on save and decrypt on read for encrypted extension")
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
    @DisplayName("Should encrypt content when changing file extension to encrypted")
    void shouldEncryptOnChangingExtension() throws IOException, NoDataByPath {
        File encrypted = new File(MOUNT_POINT_FILE, "test.txt.encrypted");
        String fileContent = "Hello, world!";

        Files.writeString(encrypted.toPath(), fileContent, StandardCharsets.UTF_8);

        testIsEncrypted(encrypted, fileContent);

        File notEncrypted = new File(MOUNT_POINT_FILE, "test.txt");
        assertRenameFile(encrypted, notEncrypted);

        testIsNotEncrypted(notEncrypted, fileContent);

        assertContainChilds(MOUNT_POINT_FILE, notEncrypted);

        assertRenameFile(notEncrypted, encrypted);
        testIsEncrypted(encrypted, fileContent);
    }

    @Disabled
    @Test
    @DisplayName("Should store Metta file and deploy it")
    void shouldStoreMettaFileAndDeployIt() throws IOException, NoDataByPath {
        testToCreateDeployableFile("metta"); // TODO: pass the correct Metta code from this line
    }

    @Test
    @Disabled
    @DisplayName("Should store Rho file and deploy it")
    void shouldStoreRhoFileAndDeployIt() throws IOException, NoDataByPath {
        testToCreateDeployableFile("rho");
    }

    @Disabled
    @Test
    @DisplayName("Should write and read large file (512MB)")
    void shouldWriteAndReadLargeFile() throws IOException, NoDataByPath, PathIsNotADirectory {
        File file = new File(MOUNT_POINT_FILE, "file.bin");

        assertCreateNewFile(file);

        byte[] inputDataAsBinary = new byte[512 * 1024 * 1024]; // 512mb
        new Random().nextBytes(inputDataAsBinary);
        assertWriteBinaryData(file, inputDataAsBinary);

        remount();

        assertWrittenData(file, inputDataAsBinary, false, "Read data should be equal to written data after remount");
    }

    @Test
    @DisplayName("Should perform CRUD operations on files: create, rename, read, and delete")
    void shouldCreateRenameGetDeleteFiles() throws IOException {

        assertUnlockWalletDirectory(REV_WALLET_1, PRIVATE_KEY_1);

        File file = new File(UNLOCKED_WALLET_DIR_1, "file.bin");

        assertCreateNewFile(file);

        assertContainChilds(UNLOCKED_WALLET_DIR_1, file);

        byte[] inputDataAsBinary = new byte[1024 * 1024]; // 1 MB
        new Random().nextBytes(inputDataAsBinary);
        assertWriteBinaryData(file, inputDataAsBinary);
        log.info("Written data length: {}", inputDataAsBinary.length);

        File renamedFile = new File(file.getParent(), "renamed.txt");
        assertRenameFile(file, renamedFile);

        assertContainChilds(UNLOCKED_WALLET_DIR_1, renamedFile);

        assertWrittenData(renamedFile, inputDataAsBinary, true,
            "Read data (from renamed file) should be equal to written data");

        String inputDataAsString = "a".repeat(1024);
        assertWriteStringData(renamedFile, inputDataAsString);

        assertContainChilds(UNLOCKED_WALLET_DIR_1, renamedFile); // it has to be the same folder after truncate and override

        File dir = new File(UNLOCKED_WALLET_DIR_1, "testDir");
        assertCreateNewDirectory(dir);

        File nestedFile = new File(dir, "nestedFile.txt");
        assertCreateNewFile(nestedFile);

        assertContainChilds(UNLOCKED_WALLET_DIR_1, renamedFile, dir);
        assertContainChilds(dir, nestedFile);

        remount(); // umount and mount back
        assertUnlockWalletDirectory(REV_WALLET_1, PRIVATE_KEY_1);

        assertContainChilds(UNLOCKED_WALLET_DIR_1, renamedFile, dir);
        assertContainChilds(dir, nestedFile);

        // check if deployed data is correct:

        assertContainChilds(UNLOCKED_WALLET_DIR_1, renamedFile, dir);
        assertContainChilds(dir, nestedFile);

        String readDataAfterRemount = Files.readString(renamedFile.toPath());
        assertEquals(inputDataAsString, readDataAfterRemount, "Read data should be equal to written data");

        assertDeleteFile(renamedFile);
        assertDeleteFile(nestedFile);
        assertDeleteDirectory(dir);

        assertContainChilds(UNLOCKED_WALLET_DIR_1); // empty

    }

    @Test
    @DisplayName("Should perform CRUD operations on directories: create, rename, list, and delete")
    void shouldCreateRenameListDeleteDirectories() {
        assertUnlockWalletDirectory(REV_WALLET_1, PRIVATE_KEY_1);

        assertContainTokenDirectory(UNLOCKED_WALLET_DIR_1);

        File dir1 = new File(UNLOCKED_WALLET_DIR_1, "testDir");

        assertCreateNewDirectory(dir1);

        assertContainChilds(UNLOCKED_WALLET_DIR_1, dir1);

        File renamedDir = new File(UNLOCKED_WALLET_DIR_1, "renamedDir");

        assertRenameFile(dir1, renamedDir);

        assertContainChilds(UNLOCKED_WALLET_DIR_1, renamedDir);

        File nestedDir1 = new File(renamedDir, "nestedDir1");
        File nestedDir2 = new File(nestedDir1, "nestedDir2");

        boolean created = nestedDir2.mkdirs();
        assertTrue(created, "Should be able to create nested directories");
        assertTrue(nestedDir1.exists(), "nestedDir1 should exist after mkdirs");
        assertTrue(nestedDir2.exists(), "nestedDir2 should exist after mkdirs");

        assertContainChilds(renamedDir, nestedDir1);

        assertDeleteFile(nestedDir2);

        assertContainChilds(nestedDir1);

        assertDeleteFile(nestedDir1);

        assertContainChilds(renamedDir);

        assertDeleteFile(renamedDir);

        assertContainChilds(UNLOCKED_WALLET_DIR_1);
    }

    @Test
    @DisplayName("Should properly handle operations on non-existent files and directories")
    void shouldHandleOperationsWithNotExistingFileAndDirectory() {
        assertThrows(WalletUnlockException.class, () -> simulateUnlockWalletDirectoryAction(REV_WALLET_1, "abc")); // invalid private key
        assertThrows(WalletUnlockException.class, () -> simulateUnlockWalletDirectoryAction(REV_WALLET_1, PRIVATE_KEY_2)); // wrong private key

        assertUnlockWalletDirectory(REV_WALLET_1, PRIVATE_KEY_1); // valid private key

        File dir = new File(UNLOCKED_WALLET_DIR_1, "testDir");

        assertNonExistentDirectoryOperationsFail(dir);

        File file = new File(UNLOCKED_WALLET_DIR_1, "testFile.txt");

        assertNonExistentFileOperationsFail(file);

        assertContainChilds(UNLOCKED_WALLET_DIR_1); // empty

        // test with client2
        // folder is locked, so it should not be visible
        assertContainChildsLocally(UNLOCKED_WALLET_DIR_1); // empty

    }

    @Test
    void shouldSupportTokenFileOperations() {
        int BIGGER_DENOMINATION = 1000000;
        int SMALLER_DENOMINATION = BIGGER_DENOMINATION / 10;

        assertFalse(new File(LOCKED_WALLET_DIR_1, TokenDirectory.NAME).exists(),
            "Token directory should not exist before unlock");

        assertDirIsEmptyLocally(LOCKED_WALLET_DIR_1); // empty because of locked

        assertUnlockWalletDirectory(REV_WALLET_1, PRIVATE_KEY_1);

        File tokenDirectory = new File(UNLOCKED_WALLET_DIR_1, TokenDirectory.NAME);

        assertContainTokenDirectory(UNLOCKED_WALLET_DIR_1);
        assertContainChilds(UNLOCKED_WALLET_DIR_1); // empty yet because of new token directory

        // .tokens directory should contain *.token files only
        File[] tokensFiles = tokenDirectory.listFiles();
        assertNotNull(tokensFiles, "Token directory should contain *.token files");
        for (File tokenFile : tokensFiles) {
            assertTokenFileProperties(tokenFile);
        }

        // .tokens directory should be closed to changes
        assertThrows(IOException.class, () -> Files.writeString(
            new File(UNLOCKED_WALLET_DIR_1 + PathUtils.getPathDelimiterBasedOnOS() + TokenDirectory.NAME, "test.token")
                .toPath(),
            "test", StandardCharsets.UTF_8), "write should return error");

        // create token file manually
        File tokenFile = new File(UNLOCKED_WALLET_DIR_1 + PathUtils.getPathDelimiterBasedOnOS() + TokenDirectory.NAME,
            "x.token");
        assertFalse(tokenFile.exists(), "Token file should not exist");
        assertThrows(IOException.class, tokenFile::createNewFile, "Failed to create token file");

        File tokenFileToChange = Arrays.stream(tokensFiles)
            .filter(file -> file.getName().startsWith(BIGGER_DENOMINATION + "-REV"))
            .findFirst()
            .orElseThrow(
                () -> new RuntimeException("Token file not found in list " + Arrays.toString(tokensFiles)));

        assertDoesNotThrow(() -> simulateChangeTokenAction(tokenFileToChange.getAbsolutePath()));

        // only one token file should be effected by change
        assertFalse(tokenFileToChange.exists(), "Token file should not exist");

        File[] otherTokenFiles = Arrays.stream(tokensFiles)
            .filter(file -> !file.getName().equals(tokenFileToChange.getName()))
            .toArray(File[]::new);

        // other token files should not be effected by change
        for (File otherTokenFile : otherTokenFiles) {
            assertTrue(otherTokenFile.exists(), "Token file should exist");
        }

        File[] allTokenFiles = tokenDirectory.listFiles();
        assertNotNull(allTokenFiles, "Token directory should contain *.token files");

        Set<File> newTokenFilesAfterChange = Set.of(allTokenFiles)
            .stream()
            .filter(file -> Arrays.stream(otherTokenFiles)
                .noneMatch(file2 -> file2.getName().equals(file.getName())))
            .collect(Collectors.toSet());

        // 10 because of denomination (e.g. 10 => 1,1,1,1,1,1,1,1,1,1)
        assertEquals(10, newTokenFilesAfterChange.size(), "Only 10 token files should be effected by change");

        for (File newTokenFileAfterChange : newTokenFilesAfterChange) {
            assertTokenFileProperties(newTokenFileAfterChange);
            assertTrue(
                newTokenFileAfterChange.getName()
                    .startsWith("%s-REV.changed-%s-REV.".formatted(SMALLER_DENOMINATION, BIGGER_DENOMINATION)),
                "Token file %s should be called in the format of %s-REV.changed-%s-REV.{N}.token"
                    .formatted(SMALLER_DENOMINATION, newTokenFileAfterChange.getName(), BIGGER_DENOMINATION));
        }


        waitOnBackgroundDeployments();

        // check balance
        long revBalance1Before = checkBalance(REV_WALLET_1);
        long revBalance2Before = checkBalance(REV_WALLET_2);

        // transfer 1 token from rev1 to rev2
        File tokenFileToTransfer = newTokenFilesAfterChange.stream().findFirst().orElseThrow(() ->
            new RuntimeException("Token file not found"));
        // rev1 -> rev2
        File transferTarget = new File(LOCKED_WALLET_DIR_2, tokenFileToTransfer.getName());
        assertRenameFile(tokenFileToTransfer, transferTarget);

        waitOnBackgroundDeployments();

        // wait on transfer because of the immediate check could get fail sometime
        try {
            Thread.sleep(3000);
        } catch (InterruptedException ignored) {
        }

        long revBalance1Expected = revBalance1Before - SMALLER_DENOMINATION;
        long revBalance2Expected = revBalance2Before + SMALLER_DENOMINATION;

        // check balance
        long revBalance1Actual = checkBalance(REV_WALLET_1);
        long revBalance2Actual = checkBalance(REV_WALLET_2);

        long avg_deployment_cost = 50_000L;

        assertEquals(revBalance1Expected, revBalance1Actual, avg_deployment_cost,
            "Balance of wallet 1 should be decreased. Balance before: " + revBalance1Before + ", balance after: " + revBalance1Actual);
        assertEquals(revBalance2Expected, revBalance2Actual,
            "Balance of wallet 2 should be increased. Balance before: " + revBalance2Before + ", balance after: " + revBalance2Actual);

    }

    @Test
    @DisplayName("Should properly track and update last modified dates for files and directories")
    void shouldTrackLastModifiedDatesForFilesAndDirectories() throws IOException {
        assertUnlockWalletDirectory(REV_WALLET_1, PRIVATE_KEY_1);

        // Test file creation and last modified time
        File testFile = new File(UNLOCKED_WALLET_DIR_1, "testFile.txt");
        long beforeFileCreation = waitForTimestampChange();
        
        assertCreateNewFile(testFile);
        
        long afterFileCreation = System.currentTimeMillis();
        assertLastModifiedTimeAfter(testFile, beforeFileCreation, 
            "File creation operation should set last modified time to creation timestamp");
        assertTrue(testFile.lastModified() <= afterFileCreation, 
            "File last modified time validation failed - timestamp should not be in the future. " +
            "File: " + testFile.getAbsolutePath() + ", " +
            "Last modified: " + testFile.lastModified() + " (" + new java.util.Date(testFile.lastModified()) + "), " +
            "After creation: " + afterFileCreation + " (" + new java.util.Date(afterFileCreation) + ")");

        // Wait and modify file content
        long beforeContentModification = waitForTimestampChange();
        String initialContent = "Initial content";
        assertWriteStringData(testFile, initialContent);
        
        assertLastModifiedTimeUpdated(testFile, beforeContentModification, "writing initial content to file");

        // Test directory creation and last modified time
        File testDir = new File(UNLOCKED_WALLET_DIR_1, "testDirectory");
        long beforeDirCreation = waitForTimestampChange();
        
        assertCreateNewDirectory(testDir);
        
        assertLastModifiedTimeUpdated(testDir, beforeDirCreation, "directory creation");

        // Test that adding files to directory updates directory's last modified time
        long dirInitialTime = testDir.lastModified();
        long beforeAddingFileToDir = waitForTimestampChange();
        
        File fileInDir = new File(testDir, "fileInDirectory.txt");
        assertCreateNewFile(fileInDir);
        
        // Note: Directory last modified time behavior may vary by filesystem
        // Some filesystems update parent directory time when files are added
        long dirTimeAfterAddingFile = testDir.lastModified();
        log.info("Directory timestamp tracking - Dir: {}, Before adding file: {} ({}), After: {} ({}), Changed: {}", 
            testDir.getAbsolutePath(),
            dirInitialTime, new java.util.Date(dirInitialTime),
            dirTimeAfterAddingFile, new java.util.Date(dirTimeAfterAddingFile),
            dirTimeAfterAddingFile != dirInitialTime);

        // Test file rename updates last modified time
        long beforeRename = waitForTimestampChange();
        File renamedFile = new File(UNLOCKED_WALLET_DIR_1, "renamedFile.txt");
        
        assertRenameFile(testFile, renamedFile);
        
        // Wait after rename to ensure timestamp is properly set
        try {
            Thread.sleep(100); // Short wait to ensure filesystem operations complete
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Renamed file should have updated last modified time
        assertLastModifiedTimeUpdated(renamedFile, beforeRename, "file rename operation");

        // Test directory rename updates last modified time
        long beforeDirRename = waitForTimestampChange();
        File renamedDir = new File(UNLOCKED_WALLET_DIR_1, "renamedDirectory");
        
        assertRenameFile(testDir, renamedDir);
        
        // Wait after directory rename to ensure timestamp is properly set
        try {
            Thread.sleep(100); // Short wait to ensure filesystem operations complete
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        assertLastModifiedTimeUpdated(renamedDir, beforeDirRename, "directory rename operation");

        // Test that different files have different last modified times
        File anotherFile = new File(UNLOCKED_WALLET_DIR_1, "anotherFile.txt");
        waitForTimestampChange();
        assertCreateNewFile(anotherFile);
        
        // Wait after file creation to ensure timestamp is properly set
        try {
            Thread.sleep(100); // Short wait to ensure filesystem operations complete
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        assertDifferentLastModifiedTimes(renamedFile, anotherFile, 
            "Sequential file operations at different time points should result in distinct timestamps");

        // Test file content modification updates time
        String originalContent = Files.readString(renamedFile.toPath());
        long beforeContentUpdate = waitForTimestampChange();
        String updatedContent = originalContent + " - UPDATED";
        
        assertWriteStringData(renamedFile, updatedContent);
        
        // Wait after content update to ensure timestamp is properly set
        try {
            Thread.sleep(100); // Short wait to ensure filesystem operations complete
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        assertLastModifiedTimeUpdated(renamedFile, beforeContentUpdate, "file content modification");

        // Verify content was actually updated
        String readContent = Files.readString(renamedFile.toPath());
        assertEquals(updatedContent, readContent, 
            "File content validation failed after modification. " +
            "Expected content length: " + updatedContent.length() + " bytes, " +
            "Actual content length: " + readContent.length() + " bytes, " +
            "File: " + renamedFile.getAbsolutePath());

        // Test persistence after remount
        long fileTimeBeforeRemount = renamedFile.lastModified();
        long dirTimeBeforeRemount = renamedDir.lastModified();
        long anotherFileTimeBeforeRemount = anotherFile.lastModified();

        remount();
        assertUnlockWalletDirectory(REV_WALLET_1, PRIVATE_KEY_1);

        // Files should still exist and have the same last modified times after remount
        assertTrue(renamedFile.exists(), "Post-remount validation failed: Renamed file should still exist - " + renamedFile.getAbsolutePath());
        assertTrue(renamedDir.exists(), "Post-remount validation failed: Renamed directory should still exist - " + renamedDir.getAbsolutePath());
        assertTrue(anotherFile.exists(), "Post-remount validation failed: Another file should still exist - " + anotherFile.getAbsolutePath());

        assertLastModifiedTimeApproximately(renamedFile, fileTimeBeforeRemount, 
            "Persistence validation for renamed file after filesystem remount operation");
        assertLastModifiedTimeApproximately(renamedDir, dirTimeBeforeRemount, 
            "Persistence validation for renamed directory after filesystem remount operation");
        assertLastModifiedTimeApproximately(anotherFile, anotherFileTimeBeforeRemount, 
            "Persistence validation for additional file after filesystem remount operation");

    }
}
