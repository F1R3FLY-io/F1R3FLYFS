package io.f1r3fly.f1r3drive.app;

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
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static io.f1r3fly.f1r3drive.app.F1r3flyFSTestUtils.*;
import static io.f1r3fly.f1r3drive.app.F1r3flyFSAssertions.*;
import static io.f1r3fly.f1r3drive.app.F1r3flyFSHelpers.*;

class F1R3FlyFuseTest extends F1r3flyFSTestUtils {

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
        long start = startTimeTracking();

        assertUnlockWalletDirectory(client1Wallet, client1PrivateKey);

        File file = new File(client1Directory, "file.bin");

        assertCreateNewFile(file);

        assertContainChilds(client1Directory, file);

        byte[] inputDataAsBinary = new byte[1024 * 1024]; // 1 MB
        new Random().nextBytes(inputDataAsBinary);
        assertWriteBinaryData(file, inputDataAsBinary);
        log.info("Written data length: {}", inputDataAsBinary.length);

        File renamedFile = new File(file.getParent(), "renamed.txt");
        assertRenameFile(file, renamedFile);

        assertContainChilds(client1Directory, renamedFile);

        assertWrittenData(renamedFile, inputDataAsBinary, true,
                "Read data (from renamed file) should be equal to written data");

        String inputDataAsString = "a".repeat(1024);
        assertWriteStringData(renamedFile, inputDataAsString);

        assertContainChilds(client1Directory, renamedFile); // it has to be the same folder after truncate and override

        File dir = new File(client1Directory, "testDir");
        assertCreateNewDirectory(dir);

        File nestedFile = new File(dir, "nestedFile.txt");
        assertCreateNewFile(nestedFile);

        assertContainChilds(client1Directory, renamedFile, dir);
        assertContainChilds(dir, nestedFile);

        remount(); // umount and mount back

        // check if deployed data is correct:

        assertContainChilds(client1Directory, renamedFile, dir);
        assertContainChilds(dir, nestedFile);

        String readDataAfterRemount = Files.readString(renamedFile.toPath());
        assertEquals(inputDataAsString, readDataAfterRemount, "Read data should be equal to written data");

        assertDeleteFile(renamedFile);
        assertDeleteFile(nestedFile);
        assertDeleteDirectory(dir);

        assertContainChilds(client1Directory); // empty

        printElapsedTime(start);
    }

    @Test
    @DisplayName("Should perform CRUD operations on directories: create, rename, list, and delete")
    void shouldCreateRenameListDeleteDirectories() {
        assertUnlockWalletDirectory(client1Wallet, client1PrivateKey);

        assertContainTokenDirectory(client1Directory);

        File dir1 = new File(client1Directory, "testDir");

        long start = startTimeTracking();

        assertCreateNewDirectory(dir1);

        assertContainChilds(client1Directory, dir1);

        File renamedDir = new File(client1Directory, "renamedDir");
        assertRenameFile(dir1, renamedDir);

        assertContainChilds(client1Directory, renamedDir);

        File nestedDir1 = new File(renamedDir, "nestedDir1");
        File nestedDir2 = new File(nestedDir1, "nestedDir2");
        assertTrue(nestedDir2.mkdirs(), "Failed to create nested directories");

        assertContainChilds(renamedDir, nestedDir1);
        assertContainChilds(nestedDir1, nestedDir2);

        assertFalse(renamedDir.delete(), "Failed to delete non-empty directory");

        assertDeleteDirectory(nestedDir2);
        assertDeleteDirectory(nestedDir1);
        assertDeleteDirectory(renamedDir);

        assertContainChilds(client1Directory); // empty

        printElapsedTime(start);
    }

    @Test
    @DisplayName("Should properly handle operations on non-existent files and directories")
    void shouldHandleOperationsWithNotExistingFileAndDirectory() {
        assertUnlockWalletDirectory(client1Wallet, client1PrivateKey);

        File dir = new File(client1Directory, "testDir");

        assertNonExistentDirectoryOperationsFail(dir);

        File file = new File(client1Directory, "testFile.txt");

        assertNonExistentFileOperationsFail(file);

        assertContainChilds(client1Directory); // empty

        // test with client2
        // folder is locked, so it should not be visible
        assertContainChildsLocally(client2Directory); // empty

    }

    @Test
    void shouldSupportTokenFileOperations() {
        File lockedClient1Wallet = new File(MOUNT_POINT_FILE, "LOCKED-REMOTE-REV-" + client1Directory.getName());

        assertFalse(new File(lockedClient1Wallet, TokenDirectory.NAME).exists(),
                "Token directory should not exist before unlock");

        assertDirIsEmptyLocally(lockedClient1Wallet); // empty because of locked

        assertUnlockWalletDirectory(client1Wallet, client1PrivateKey);

        File tokenDirectory = new File(client1Directory, TokenDirectory.NAME);

        assertContainTokenDirectory(client1Directory);
        assertContainChilds(client1Directory); // empty yet because of new token directory

        // .tokens directory should contain *.token files only
        File[] tokensFiles = tokenDirectory.listFiles();
        assertNotNull(tokensFiles, "Token directory should contain *.token files");
        for (File tokenFile : tokensFiles) {
            assertTokenFileProperties(tokenFile);
        }

        // .tokens directory should be closed to changes
        assertThrows(IOException.class, () -> Files.writeString(
                new File(client1Directory + PathUtils.getPathDelimiterBasedOnOS() + TokenDirectory.NAME, "test.token")
                        .toPath(),
                "test", StandardCharsets.UTF_8), "write should return error");

        // create token file manually
        File tokenFile = new File(client1Directory + PathUtils.getPathDelimiterBasedOnOS() + TokenDirectory.NAME,
                "x.token");
        assertFalse(tokenFile.exists(), "Token file should not exist");
        assertThrows(IOException.class, tokenFile::createNewFile, "Failed to create token file");

        File tokenFileToExchange = Arrays.stream(tokensFiles)
                .filter(file -> file.getName().startsWith("10000000000000000-REV"))
                .findFirst()
                .orElseThrow(
                        () -> new RuntimeException("Token file not found in list " + Arrays.toString(tokensFiles)));

        assertDoesNotThrow(() -> simulateExchangeTokenAction(tokenFileToExchange.getAbsolutePath()));

        // only one token file should be effected by exchange
        assertFalse(tokenFileToExchange.exists(), "Token file should not exist");

        File[] otherTokenFiles = Arrays.stream(tokensFiles)
                .filter(file -> !file.getName().equals(tokenFileToExchange.getName()))
                .toArray(File[]::new);

        // other token files should not be effected by exchange
        for (File otherTokenFile : otherTokenFiles) {
            assertTrue(otherTokenFile.exists(), "Token file should exist");
        }

        File[] allTokenFiles = tokenDirectory.listFiles();
        assertNotNull(allTokenFiles, "Token directory should contain *.token files");

        Set<File> newTokenFilesAfterExchange = Set.of(allTokenFiles)
                .stream()
                .filter(file -> Arrays.stream(otherTokenFiles)
                        .noneMatch(file2 -> file2.getName().equals(file.getName())))
                .collect(Collectors.toSet());

        // 10 because of denomination (e.g. 10 => 1,1,1,1,1,1,1,1,1,1)
        assertEquals(10, newTokenFilesAfterExchange.size(), "Only 10 token files should be effected by exchange");

        for (File newTokenFileAfterExchange : newTokenFilesAfterExchange) {
            assertTokenFileProperties(newTokenFileAfterExchange);
            assertTrue(
                    newTokenFileAfterExchange.getName()
                            .startsWith("1000000000000000-REV.exchanged-10000000000000000-REV."),
                    "Token file %s should be called in the format of 1000000000000000-REV.exchanged-10000000000000000-REV.{N}.token"
                            .formatted(newTokenFileAfterExchange.getName()));
        }

    }
}
