package io.f1r3fly.f1r3drive.app;

import io.f1r3fly.f1r3drive.errors.*;
import io.f1r3fly.f1r3drive.fuse.ErrorCodes;
import io.f1r3fly.f1r3drive.fuse.FuseFillDir;
import io.f1r3fly.f1r3drive.fuse.FuseStubFS;
import io.f1r3fly.f1r3drive.fuse.SuccessCodes;
import io.f1r3fly.f1r3drive.contextmenu.handler.FinderSyncExtensionServiceServer;
import io.f1r3fly.f1r3drive.filesystem.FileSystem;
import io.f1r3fly.f1r3drive.blockchain.client.F1r3flyBlockchainClient;
import io.f1r3fly.f1r3drive.filesystem.InMemoryFileSystem;
import io.f1r3fly.f1r3drive.fuse.struct.FileStat;
import io.f1r3fly.f1r3drive.fuse.struct.FuseFileInfo;
import io.f1r3fly.f1r3drive.fuse.struct.Statvfs;
import io.f1r3fly.f1r3drive.filesystem.utils.PathUtils;
import jnr.ffi.Pointer;
import jnr.ffi.types.mode_t;
import jnr.ffi.types.off_t;
import jnr.ffi.types.size_t;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;
import java.io.File;

public class F1r3flyFuse extends FuseStubFS {

    private static final Logger LOGGER = LoggerFactory.getLogger(F1r3flyFuse.class);

    private final String[] MOUNT_OPTIONS = {
        // refers to https://github.com/osxfuse/osxfuse/wiki/Mount-options
        "-o", "noappledouble",
        "-o", "daemon_timeout=3600", // 1 hour timeout
        "-o", "defer_permissions", // permission is not supported that, this disables the permission check from
        // Fuse side
        "-o", "local",
        "-o", "allow_other",
        "-o", "auto_cache"
    };
    private FileSystem fileSystem;
    private F1r3flyBlockchainClient f1R3FlyBlockchainClient;
    private FinderSyncExtensionServiceServer finderSyncExtensionServiceServer;

    public F1r3flyFuse(F1r3flyBlockchainClient f1R3FlyBlockchainClient) {
        super(); // no need to call Fuse constructor?
        this.f1R3FlyBlockchainClient = f1R3FlyBlockchainClient; // doesnt have a state, so can be reused between mounts
    }

    /**
     * Common error handling for FUSE operations
     */
    @FunctionalInterface
    private interface FuseOperation {
        int execute() throws Exception;
    }

    private int executeWithErrorHandling(String operationName, String path, FuseOperation operation) {
        boolean isTrace = operationName.equals("Getattr") || operationName.equals("Read")
            || operationName.equals("Write") || operationName.equals("Statfs");
        if (isTrace) {
            LOGGER.trace("Called {} {}", operationName, path);
        } else {
            LOGGER.debug("Called {} {}", operationName, path);
        }

        // Check if filesystem is mounted first
        if (notMounted()) {
            LOGGER.debug("{} - FileSystem not mounted for path: {}", operationName, path);
            return -ErrorCodes.EIO();
        }

        try {
            return operation.execute();
        } catch (FileAlreadyExists e) {
            if (isTrace) {
                LOGGER.trace("{} - File/Directory already exists: {}", operationName, path, e);
            } else {
                LOGGER.debug("{} - File/Directory already exists: {}", operationName, path, e);
            }
            return -ErrorCodes.EEXIST();
        } catch (PathNotFound e) {
            if (isTrace) {
                LOGGER.trace("{} - Path not found: {}", operationName, path, e);
            } else {
                LOGGER.debug("{} - Path not found: {}", operationName, path, e);
            }
            return -ErrorCodes.ENOENT();
        } catch (OperationNotPermitted e) {
            if (isTrace) {
                LOGGER.trace("{} - Operation not permitted: {}", operationName, path, e);
            } else {
                LOGGER.debug("{} - Operation not permitted: {}", operationName, path, e);
            }
            return -ErrorCodes.EPERM();
        } catch (PathIsNotAFile e) {
            if (isTrace) {
                LOGGER.trace("{} - Path {} is not a file", operationName, path, e);
            } else {
                LOGGER.info("{} - Path {} is not a file", operationName, path, e);
            }
            return -ErrorCodes.EISDIR();
        } catch (PathIsNotADirectory e) {
            if (isTrace) {
                LOGGER.trace("{} - Path {} is not a directory", operationName, path, e);
            } else {
                LOGGER.info("{} - Path {} is not a directory", operationName, path, e);
            }
            return -ErrorCodes.ENOTDIR();
        } catch (DirectoryNotEmpty e) {
            if (isTrace) {
                LOGGER.trace("{} - Directory {} is not empty", operationName, path);
            } else {
                LOGGER.info("{} - Directory {} is not empty", operationName, path);
            }
            return -ErrorCodes.ENOTEMPTY();
        } catch (IOException e) {
            if (isTrace) {
                LOGGER.trace("{} - IO error for path {}", operationName, path, e);
            } else {
                LOGGER.warn("{} - IO error for path {}", operationName, path, e);
            }
            return -ErrorCodes.EIO();
        } catch (Exception e) {
            if (isTrace) {
                LOGGER.trace("{} - Unexpected error for path {}", operationName, path, e);
            } else {
                LOGGER.error("{} - Unexpected error for path {}", operationName, path, e);
            }
            return -ErrorCodes.EIO();
        }
    }

    @Override
    public int create(String path, @mode_t long mode, FuseFileInfo fi) {
        return executeWithErrorHandling("Create", path, () -> {
            // Reject creation of Apple metadata files
            if (PathUtils.isAppleMetadataFile(path)) {
                LOGGER.debug("Rejecting creation of Apple metadata file: {}", path);
                return -ErrorCodes.EACCES();
            }
            fileSystem.createFile(path, mode);
            return SuccessCodes.OK;
        });
    }

    @Override
    public int getattr(String path, FileStat stat) {
        return executeWithErrorHandling("Getattr", path, () -> {
            // Explicitly reject Apple metadata files
            if (PathUtils.isAppleMetadataFile(path)) {
                LOGGER.debug("Rejecting Apple metadata file: {}", path);
                return -ErrorCodes.ENOENT();
            }
            fileSystem.getAttributes(path, stat, getContext());
            return SuccessCodes.OK;
        });
    }

    @Override
    public int mkdir(String path, @mode_t long mode) {
        return executeWithErrorHandling("Mkdir", path, () -> {
            fileSystem.makeDirectory(path, mode);
            return SuccessCodes.OK;
        });
    }

    @Override
    public int read(String path, Pointer buf, @size_t long size, @off_t long offset, FuseFileInfo fi) {
        return executeWithErrorHandling("Read", path, () -> {
            return fileSystem.readFile(path, buf, size, offset);
        });
    }

    @Override
    public int readdir(String path, Pointer buf, FuseFillDir filter, @off_t long offset, FuseFileInfo fi) {
        return executeWithErrorHandling("Readdir", path, () -> {
            fileSystem.readDirectory(path, buf, filter);
            return SuccessCodes.OK;
        });
    }

    @Override
    public int statfs(String path, Statvfs stbuf) {
        return executeWithErrorHandling("Statfs", path, () -> {
            fileSystem.getFileSystemStats(path, stbuf);
            return super.statfs(path, stbuf);
        });
    }

    @Override
    public int rename(String path, String newName) {
        return executeWithErrorHandling("Rename", path, () -> {
            fileSystem.renameFile(path, newName);
            return SuccessCodes.OK;
        });
    }

    @Override
    public int rmdir(String path) {
        return executeWithErrorHandling("Rmdir", path, () -> {
            fileSystem.removeDirectory(path);
            return SuccessCodes.OK;
        });
    }

    @Override
    public int truncate(String path, long offset) {
        return executeWithErrorHandling("Truncate", path, () -> {
            fileSystem.truncateFile(path, offset);
            return SuccessCodes.OK;
        });
    }

    @Override
    public int unlink(String path) {
        return executeWithErrorHandling("Unlink", path, () -> {
            fileSystem.unlinkFile(path);
            return SuccessCodes.OK;
        });
    }

    @Override
    public int open(String path, FuseFileInfo fi) {
        return executeWithErrorHandling("Open", path, () -> {
            // Reject opening Apple metadata files
            if (PathUtils.isAppleMetadataFile(path)) {
                LOGGER.debug("Rejecting open of Apple metadata file: {}", path);
                return -ErrorCodes.ENOENT();
            }
            fileSystem.openFile(path);
            LOGGER.debug("Opened file {}", path);
            return SuccessCodes.OK;
        });
    }

    @Override
    public int write(String path, Pointer buf, @size_t long size, @off_t long offset, FuseFileInfo fi) {
        return executeWithErrorHandling("Write", path, () -> {
            return fileSystem.writeFile(path, buf, size, offset);
        });
    }

    @Override
    public int flush(String path, FuseFileInfo fi) {
        return executeWithErrorHandling("Flush", path, () -> {
            fileSystem.flushFile(path);
            return SuccessCodes.OK;
        });
    }

    @Override
    public void mount(Path mountPoint, boolean blocking, boolean debug, String[] fuseOpts) {
        LOGGER.debug("Called Mounting F1r3flyFuse on {} with opts {}", mountPoint, Arrays.toString(fuseOpts));

        try {
            // Ensure mount point exists and is accessible
            File mountPointFile = mountPoint.toFile();
            if (!mountPointFile.exists()) {
                LOGGER.debug("Mount point does not exist, creating: {}", mountPoint);
                if (!mountPointFile.mkdirs()) {
                    throw new RuntimeException("Failed to create mount point directory: " + mountPoint);
                }
            }
            if (!mountPointFile.isDirectory()) {
                throw new RuntimeException("Mount point is not a directory: " + mountPoint);
            }
            if (!mountPointFile.canRead() || !mountPointFile.canWrite()) {
                throw new RuntimeException("Mount point is not accessible (read/write): " + mountPoint);
            }
            LOGGER.debug("Mount point verified: {}", mountPoint);

            // combine fuseOpts and MOUNT_OPTIONS
            String[] allFuseOpts = Arrays.copyOf(fuseOpts, fuseOpts.length + MOUNT_OPTIONS.length);
            System.arraycopy(MOUNT_OPTIONS, 0, allFuseOpts, fuseOpts.length, MOUNT_OPTIONS.length);

            LOGGER.debug("Creating InMemoryFileSystem...");
            this.fileSystem = new InMemoryFileSystem(f1R3FlyBlockchainClient);
            LOGGER.debug("Created InMemoryFileSystem successfully");

            LOGGER.debug("Creating FinderSyncExtensionServiceServer...");
            this.finderSyncExtensionServiceServer = new FinderSyncExtensionServiceServer(
                this::handleChange, this::handleUnlockRevDirectory, 54000);
            LOGGER.debug("Created FinderSyncExtensionServiceServer successfully");

            this.mountName = "F1r3flyFuse-" + UUID.randomUUID();
            LOGGER.debug("Generated mount name: {}", this.mountName);

            LOGGER.debug("Waiting for background operations to complete...");
            waitOnBackgroundThread();

            LOGGER.debug("Starting FinderSyncExtensionServiceServer...");
            finderSyncExtensionServiceServer.start();
            LOGGER.debug("Started FinderSyncExtensionServiceServer successfully");

            LOGGER.debug("Mounting FUSE filesystem with options: {}", Arrays.toString(allFuseOpts));
            super.mount(mountPoint, blocking, debug, allFuseOpts);

            LOGGER.info("Successfully mounted F1r3flyFuse on {} with name {}", mountPoint, this.mountName);

        } catch (RuntimeException e) {
            LOGGER.error("Runtime error during mount: {}", e.getMessage(), e);
            cleanupResources();
            throw e;
        } catch (Throwable e) {
            LOGGER.error("Error mounting F1r3flyFuse on {}: {}", mountPoint, e.getMessage(), e);
            cleanupResources();
            throw new RuntimeException("Failed to mount F1r3flyFuse", e);
        }
    }

    private void cleanupResources() {
        LOGGER.debug("Starting cleanup of resources...");
        // destroy background tasks and queue
        if (this.fileSystem != null) {
            LOGGER.debug("Terminating filesystem...");
            this.fileSystem.terminate();
            this.fileSystem = null;
            LOGGER.debug("Filesystem terminated and set to null");
        } else {
            LOGGER.debug("Filesystem was already null, skipping termination");
        }
        if (this.finderSyncExtensionServiceServer != null) {
            LOGGER.debug("Stopping FinderSyncExtensionServiceServer...");
            this.finderSyncExtensionServiceServer.stop();
            LOGGER.debug("FinderSyncExtensionServiceServer stopped");
        } else {
            LOGGER.debug("FinderSyncExtensionServiceServer was already null, skipping stop");
        }
        LOGGER.debug("Resource cleanup completed");
    }

    public void waitOnBackgroundThread() {
        try {
            if (fileSystem != null) {
                LOGGER.debug("Waiting for background deploy operations to complete...");
                fileSystem.waitOnBackgroundDeploy();
                LOGGER.debug("Background deploy operations completed successfully");
            } else {
                LOGGER.warn("waitOnBackgroundThread called but fileSystem is null");
            }
        } catch (Throwable e) {
            LOGGER.error("Error waiting for background thread operations to complete", e);
        }
    }

    @Override
    public void umount() {
        LOGGER.debug("Called Umounting F1r3flyFuse. Mounted: {}, filesystem {}", mounted.get(), fileSystem != null);
        try {
            LOGGER.debug("Waiting for background operations to complete before unmount...");
            waitOnBackgroundThread();
            LOGGER.debug("Background operations completed, calling super.umount()...");
            super.umount();
            LOGGER.debug("super.umount() completed, starting cleanup...");
            cleanupResources();
            LOGGER.info("Successfully unmounted F1r3flyFuse");
        } catch (RuntimeException e) {
            LOGGER.error("Runtime error during unmount: {}", e.getMessage(), e);
            // Still cleanup on error
            try {
                cleanupResources();
            } catch (Exception cleanupError) {
                LOGGER.error("Error during cleanup after unmount failure", cleanupError);
            }
            throw e;
        } catch (Throwable e) {
            LOGGER.error("Error unmounting F1r3flyFuse: {}", e.getMessage(), e);
            // Still cleanup on error
            try {
                cleanupResources();
            } catch (Exception cleanupError) {
                LOGGER.error("Error during cleanup after unmount failure", cleanupError);
            }
            throw new RuntimeException("Failed to unmount F1r3flyFuse", e);
        }
    }

    protected boolean notMounted() {
        boolean mountedFlag = mounted.get();
        boolean fileSystemExists = fileSystem != null;
        boolean isNotMounted = !mountedFlag || !fileSystemExists;

        if (isNotMounted) {
            LOGGER.warn("Filesystem is not mounted. mounted.get()={}, fileSystem!=null={}", mountedFlag,
                fileSystemExists);
            // Add stack trace to help debug why filesystem becomes null
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("notMounted() called from:", new Exception("Stack trace"));
            }
        }

        return isNotMounted;
    }

    private FinderSyncExtensionServiceServer.Result handleChange(String tokenFilePath) {
        LOGGER.debug("Called onChange for path: {}", tokenFilePath);
        if (notMounted()) {
            LOGGER.warn("handleChange - FileSystem not mounted for path: {}", tokenFilePath);
            return FinderSyncExtensionServiceServer.Result.error("FileSystem not mounted");
        }

        try {
            String normalizedTokenFilePath = tokenFilePath.replace(mountPoint.toFile().getAbsolutePath(), "");
            fileSystem.changeTokenFile(normalizedTokenFilePath);
            LOGGER.debug("Successfully changed token file: {}", normalizedTokenFilePath);
            return FinderSyncExtensionServiceServer.Result.success();
        } catch (Exception e) {
            LOGGER.error("Error exchanging token file: {}", tokenFilePath, e);
            return FinderSyncExtensionServiceServer.Result.error(e.getMessage());
        }
    }

    private FinderSyncExtensionServiceServer.Result handleUnlockRevDirectory(String revAddress, String privateKey) {
        LOGGER.debug("Called handleUnlockRevDirectory for revAddress: {}", revAddress);

        if (notMounted()) {
            LOGGER.warn("handleUnlockRevDirectory - FileSystem not mounted for revAddress: {}", revAddress);
            return FinderSyncExtensionServiceServer.Result.error("FileSystem not mounted");
        }
        try {
            fileSystem.unlockRootDirectory(revAddress, privateKey);
            LOGGER.debug("Successfully unlocked directory for revAddress: {}", revAddress);
            return FinderSyncExtensionServiceServer.Result.success();
        } catch (Exception e) {
            LOGGER.error("Error unlocking directory for revAddress: {}", revAddress, e);
            return FinderSyncExtensionServiceServer.Result.error(e.getMessage());
        }
    }

}
