package io.f1r3fly.f1r3drive.app;

import static io.f1r3fly.f1r3drive.app.F1r3DriveAssertions.assertContainChildsLocally;
import static io.f1r3fly.f1r3drive.app.F1r3DriveAssertions.assertCreateNewDirectoryLocally;
import static io.f1r3fly.f1r3drive.app.F1r3DriveAssertions.assertFileContentAtShard;
import static io.f1r3fly.f1r3drive.app.F1r3DriveAssertions.assertUnlockWalletDirectory;
import static io.f1r3fly.f1r3drive.app.F1r3DriveAssertions.assertWrittenDataLocally;

import io.f1r3fly.f1r3drive.app.linux.fuse.F1r3DriveFuse;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class F1R3DriveSynchronizationTest extends F1R3DriveTestFixture {
  @Test
  @DisplayName("Should read f1r3node-rust data from a fresh mount after sync")
  void shouldReadF1r3nodeRustDataFromFreshMountAfterSync() throws Exception {
    mountF1r3Drive(true);
    assertUnlockWalletDirectory(REV_WALLET_1, PRIVATE_KEY_1);

    File syncedDirectory = new File(UNLOCKED_WALLET_DIR_1, "synced-directory");
    assertCreateNewDirectoryLocally(syncedDirectory);

    File syncedFile = new File(syncedDirectory, "shared.txt");
    byte[] expectedData = "f1r3node-rust synchronized content".getBytes(StandardCharsets.UTF_8);
    Files.write(syncedFile.toPath(), expectedData);

    assertWrittenDataLocally(syncedFile, expectedData, "First mount should read written data");
    assertFileContentAtShard(expectedData, syncedFile);
    waitOnBackgroundDeployments();

    f1r3DriveFuse.umount();
    Thread.sleep(1000);

    f1r3DriveFuse = new F1r3DriveFuse(f1R3FlyBlockchainClient);
    forceUmountAndCleanup();
    f1r3DriveFuse.mount(MOUNT_POINT);
    Thread.sleep(1000);
    assertUnlockWalletDirectory(REV_WALLET_1, PRIVATE_KEY_1);

    File remountedDirectory = new File(UNLOCKED_WALLET_DIR_1, syncedDirectory.getName());
    File remountedFile = new File(remountedDirectory, syncedFile.getName());

    assertContainChildsLocally(UNLOCKED_WALLET_DIR_1, remountedDirectory);
    assertContainChildsLocally(remountedDirectory, remountedFile);
    assertWrittenDataLocally(
        remountedFile, expectedData, "Fresh mount should read synchronized f1r3node-rust data");
  }
}
