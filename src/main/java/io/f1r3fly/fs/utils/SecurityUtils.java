package io.f1r3fly.fs.utils;

import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SecurityUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(SecurityUtils.class);

    public static boolean canHandleShutdownHooks() {
        SecurityManager security = System.getSecurityManager();
        if (security == null) {
            return true;
        }
        try {
            security.checkPermission(new RuntimePermission("shutdownHooks"));
            return true;
        } catch (final SecurityException e) {
            return false;
        }
    }

    private SecurityUtils() {
    }

    public static boolean isRunningOnWSL() {
        try {
            // Method 1: Check /proc/version for Microsoft/WSL strings
            Path procVersionPath = Path.of("/proc/version");
            if (java.nio.file.Files.exists(procVersionPath)) {
                String procVersion = java.nio.file.Files.readString(procVersionPath);
                if (procVersion.toLowerCase().contains("microsoft") || 
                    procVersion.toLowerCase().contains("wsl")) {
                    return true;
                }
            }
            
            // Method 2: Check for WSL environment variables
            if (System.getenv("WSL_DISTRO_NAME") != null || 
                System.getenv("WSLENV") != null) {
                return true;
            }
            
            // Method 3: Check for WSL-specific files
            if (java.nio.file.Files.exists(Path.of("/proc/sys/fs/binfmt_misc/WSLInterop"))) {
                return true;
            }
            
            return false;
        } catch (Exception e) {
            LOGGER.warn("Error detecting WSL environment", e);
            return false;
        }
    }
}
