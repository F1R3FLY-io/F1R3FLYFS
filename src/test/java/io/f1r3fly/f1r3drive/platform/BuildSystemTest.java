package io.f1r3fly.f1r3drive.platform;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Simple build system verification test.
 * Tests that platform-specific resources are correctly included in builds.
 */
public class BuildSystemTest {

    /**
     * Loads the {@code platform.properties} for a specific platform.
     *
     * <p>The test runtime classpath intentionally contains BOTH the macOS and Linux
     * source-set outputs (see {@code sourceSets.test} in build.gradle), so there are
     * TWO resources named {@code platform.properties} at the classpath root:
     * one for Linux (platform.type=linux) and one for macOS (platform.type=darwin).
     * A plain {@code getResourceAsStream("platform.properties")} resolves this
     * ambiguously and returns whichever happens to be first on the classpath, so it
     * cannot be relied upon to identify a specific platform. (In production this
     * collision never occurs: each per-platform shadowJar bundles only one platform's
     * resources.)
     *
     * <p>To disambiguate, we enumerate ALL {@code platform.properties} resources via
     * {@link ClassLoader#getResources(String)} and return the one whose
     * {@code platform.type} matches the requested platform.
     */
    private Properties loadPlatformProperties(String expectedType) throws Exception {
        Enumeration<URL> resources =
            getClass().getClassLoader().getResources("platform.properties");
        assertTrue(resources.hasMoreElements(),
                  "platform.properties should be available in classpath");

        while (resources.hasMoreElements()) {
            URL url = resources.nextElement();
            Properties props = new Properties();
            try (InputStream is = url.openStream()) {
                props.load(is);
            }
            if (expectedType.equals(props.getProperty("platform.type"))) {
                return props;
            }
        }

        fail("No platform.properties found on the test classpath with platform.type="
            + expectedType);
        return null; // unreachable
    }

    @Test
    public void testPlatformPropertiesAvailable() throws Exception {
        // Test that platform.properties is available in classpath
        InputStream propertiesStream = getClass().getClassLoader().getResourceAsStream("platform.properties");
        assertNotNull(propertiesStream, "platform.properties should be available in classpath");

        Properties platformProps = new Properties();
        platformProps.load(propertiesStream);

        // Verify basic properties exist
        assertTrue(platformProps.containsKey("platform.name"), "Should contain platform.name");
        assertTrue(platformProps.containsKey("platform.type"), "Should contain platform.type");

        String platformName = platformProps.getProperty("platform.name");
        assertNotNull(platformName, "Platform name should not be null");
        assertTrue(platformName.equals("macOS") || platformName.equals("Linux"),
                  "Platform name should be either macOS or Linux, but was: " + platformName);

        propertiesStream.close();
    }

    @Test
    @EnabledOnOs(OS.MAC)
    public void testMacOSPropertiesContent() throws Exception {
        // Select the macOS resource explicitly: the test classpath also contains the
        // Linux platform.properties, so a plain getResourceAsStream would be ambiguous.
        Properties props = loadPlatformProperties("darwin");

        assertEquals("macOS", props.getProperty("platform.name"));
        assertEquals("darwin", props.getProperty("platform.type"));
        assertEquals("10.15", props.getProperty("platform.min.version"));
        assertEquals("libf1r3drive-fsevents.dylib", props.getProperty("native.library.name"));
        assertEquals("true", props.getProperty("fileprovider.enabled"));
        assertEquals("true", props.getProperty("fsevents.enabled"));
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    public void testLinuxPropertiesContent() throws Exception {
        // Select the Linux resource explicitly: the test classpath also contains the
        // macOS platform.properties, so a plain getResourceAsStream would be ambiguous
        // and could resolve to the macOS (darwin) copy, breaking these assertions.
        Properties props = loadPlatformProperties("linux");

        assertEquals("Linux", props.getProperty("platform.name"));
        assertEquals("linux", props.getProperty("platform.type"));
        assertEquals("2.6.13", props.getProperty("platform.min.kernel.version"));
        assertEquals("2.6", props.getProperty("platform.min.fuse.version"));
        assertEquals("true", props.getProperty("fuse.enabled"));
        assertEquals("true", props.getProperty("inotify.enabled"));
        assertEquals("/dev/fuse", props.getProperty("fuse.device.path"));
    }

    @Test
    public void testJavaVersionCompatibility() {
        // Verify we're running on Java 17+ as required
        String javaVersion = System.getProperty("java.version");
        assertNotNull(javaVersion, "Java version should be available");

        // Extract major version
        String majorVersion = javaVersion.split("\\.")[0];
        int majorVersionInt = Integer.parseInt(majorVersion);

        assertTrue(majorVersionInt >= 17,
                  "Should be running on Java 17 or higher, but found: " + javaVersion);
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    public void testFUSEDependenciesAvailable() {
        // Test that FUSE-related classes are available on Linux builds
        try {
            Class.forName("ru.serce.jnrfuse.FuseStubFS");
            Class.forName("ru.serce.jnrfuse.struct.FileStat");
            Class.forName("ru.serce.jnrfuse.struct.FuseFileInfo");
            // If we get here, FUSE dependencies are available
            assertTrue(true, "FUSE dependencies should be available on Linux");
        } catch (ClassNotFoundException e) {
            fail("FUSE dependencies should be available on Linux build: " + e.getMessage());
        }
    }

    @Test
    public void testPlatformSpecificClassesAvailable() {
        // Test that platform-specific classes are in the classpath
        String osName = System.getProperty("os.name", "").toLowerCase();

        if (osName.contains("mac") || osName.contains("darwin")) {
            // On macOS, verify macOS classes are available
            try {
                Class.forName("io.f1r3fly.f1r3drive.platform.macos.MacOSPlatformInfo");
                assertTrue(true, "macOS platform classes should be available");
            } catch (ClassNotFoundException e) {
                fail("macOS platform classes should be available: " + e.getMessage());
            }
        } else if (osName.contains("linux")) {
            // On Linux, verify Linux classes are available
            try {
                Class.forName("io.f1r3fly.f1r3drive.platform.linux.LinuxPlatformInfo");
                assertTrue(true, "Linux platform classes should be available");
            } catch (ClassNotFoundException e) {
                fail("Linux platform classes should be available: " + e.getMessage());
            }
        }
    }
}
