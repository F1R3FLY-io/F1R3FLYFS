package io.f1r3fly.f1r3drive.app.linux.fuse;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class F1r3DriveFuseTest {

  private final String originalOsName = System.getProperty("os.name");

  @AfterEach
  void restoreOsName() {
    System.setProperty("os.name", originalOsName);
  }

  @Test
  void macOSMountOptionsIncludeMacFuseFinderAndNoCacheSettings() {
    System.setProperty("os.name", "Mac OS X");

    List<String> options = Arrays.asList(F1r3DriveFuse.getDefaultMountOptions());

    assertTrue(options.contains("fsname=f1r3drive"));
    assertTrue(options.contains("volname=F1r3Drive"));
    assertTrue(options.contains("local"));
    assertTrue(options.contains("noappledouble"));
    assertTrue(options.contains("noatime"));
    assertTrue(options.contains("attr_timeout=0"));
    assertTrue(options.contains("entry_timeout=0"));
    assertTrue(options.contains("negative_timeout=0"));
    assertTrue(options.contains("-s"));
  }

  @Test
  void linuxMountOptionsExcludeMacOSOnlySettings() {
    System.setProperty("os.name", "Linux");

    List<String> options = Arrays.asList(F1r3DriveFuse.getDefaultMountOptions());

    assertTrue(options.contains("fsname=f1r3drive"));
    assertTrue(options.contains("noatime"));
    assertTrue(options.contains("attr_timeout=0"));
    assertTrue(options.contains("entry_timeout=0"));
    assertTrue(options.contains("negative_timeout=0"));
    assertFalse(options.contains("volname=F1r3Drive"));
    assertFalse(options.contains("local"));
    assertFalse(options.contains("noappledouble"));
  }
}
