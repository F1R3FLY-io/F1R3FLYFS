package io.f1r3fly.fs.examples;

import io.f1r3fly.fs.ErrorCodes;
import io.f1r3fly.fs.FuseFillDir;
import io.f1r3fly.fs.FuseStubFS;
import io.f1r3fly.fs.SuccessCodes;
import io.f1r3fly.fs.contextmenu.FinderSyncExtensionServiceServer;
import io.f1r3fly.fs.examples.storage.FileSystem;
import io.f1r3fly.fs.examples.storage.errors.*;
import io.f1r3fly.fs.examples.storage.grcp.F1r3flyApi;
import io.f1r3fly.fs.examples.storage.filesystem.InMemoryFileSystem;
import io.f1r3fly.fs.struct.FileStat;
import io.f1r3fly.fs.struct.FuseFileInfo;
import io.f1r3fly.fs.struct.Statvfs;
import io.f1r3fly.fs.utils.PathUtils;
import jnr.ffi.Pointer;
import jnr.ffi.types.mode_t;
import jnr.ffi.types.off_t;
import jnr.ffi.types.size_t;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;


public class F1r3flyFS extends FuseStubFS {

    private static final Logger LOGGER = LoggerFactory.getLogger(F1r3flyFS.class);

    private final String[] MOUNT_OPTIONS = {
        // refers to https://github.com/osxfuse/osxfuse/wiki/Mount-options
        "-o", "noappledouble",
        "-o", "daemon_timeout=3600", // 1 hour timeout
        "-o", "defer_permissions", // permission is not supported that, this disables the permission check from Fuse side
        "-o", "local",
        "-o", "allow_other",
        "-o", "auto_cache"
    };
    private FileSystem fileSystem;
    private F1r3flyApi f1r3flyApi;
    private FinderSyncExtensionServiceServer finderSyncExtensionServiceServer;


    public F1r3flyFS(F1r3flyApi f1r3flyApi) {
        super(); // no need to call Fuse constructor?
        this.f1r3flyApi = f1r3flyApi; // doesnt have a state, so can be reused between mounts
    }

    /**
     * Common error handling for FUSE operations
     */
    @FunctionalInterface
    private interface FuseOperation {
        int execute() throws Exception;
    }

    private int executeWithErrorHandling(String operationName, String path, FuseOperation operation) {
        // Log the operation at the start
        if (operationName.equals("Getattr") || operationName.equals("Read") || operationName.equals("Write") || operationName.equals("Statfs")) {
            LOGGER.trace("Called {} {}", operationName, path);
        } else {
            LOGGER.debug("Called {} {}", operationName, path);
        }

        // Check if filesystem is mounted first
        if (notMounted()) {
            LOGGER.warn("{} - FileSystem not mounted for path: {}", operationName, path);
            return -ErrorCodes.EIO();
        }

        try {
            return operation.execute();
        } catch (FileAlreadyExists e) {
            LOGGER.warn("{} - File/Directory already exists: {}", operationName, path, e);
            return -ErrorCodes.EEXIST();
        } catch (PathNotFound e) {
            LOGGER.warn("{} - Path not found: {}", operationName, path, e);
            return -ErrorCodes.ENOENT();
        } catch (OperationNotPermitted e) {
            LOGGER.warn("{} - Operation not permitted: {}", operationName, path, e);
            return -ErrorCodes.EPERM();
        } catch (PathIsNotAFile e) {
            LOGGER.warn("{} - Path {} is not a file", operationName, path, e);
            return -ErrorCodes.EISDIR();
        } catch (PathIsNotADirectory e) {
            LOGGER.warn("{} - Path {} is not a directory", operationName, path, e);
            return -ErrorCodes.ENOTDIR();
        } catch (DirectoryNotEmpty e) {
            LOGGER.debug("{} - Directory {} is not empty", operationName, path);
            return -ErrorCodes.ENOTEMPTY();
        } catch (IOException e) {
            LOGGER.warn("{} - IO error for path {}", operationName, path, e);
            return -ErrorCodes.EIO();
        } catch (Throwable e) {
            LOGGER.error("{} - Unexpected error for path {}", operationName, path, e);
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
        LOGGER.debug("Called Mounting F1r3flyFS on {} with opts {}", mountPoint, Arrays.toString(fuseOpts));

        try {
            // combine fuseOpts and MOUNT_OPTIONS
            String[] allFuseOpts = Arrays.copyOf(fuseOpts, fuseOpts.length + MOUNT_OPTIONS.length);
            System.arraycopy(MOUNT_OPTIONS, 0, allFuseOpts, fuseOpts.length, MOUNT_OPTIONS.length);

            this.fileSystem = new InMemoryFileSystem(f1r3flyApi);
            this.finderSyncExtensionServiceServer = new FinderSyncExtensionServiceServer(
                this::handleExchange, this::handleUnlockRevDirectory, 54000
            );

            this.mountName = "F1r3flyFS"; // TODO: generate a random mount name for each mount

            waitOnBackgroundThread();

            finderSyncExtensionServiceServer.start();

            LOGGER.debug("Server started");

            super.mount(mountPoint, blocking, debug, allFuseOpts);

            LOGGER.debug("Mounted F1r3flyFS on {} with name {}", mountPoint, this.mountName);

        } catch (RuntimeException e) {
            LOGGER.error("Runtime error during mount: {}", e.getMessage(), e);
            cleanupResources();
            throw e;
        } catch (Throwable e) {
            LOGGER.error("Error mounting F1r3flyFS on {}: {}", mountPoint, e.getMessage(), e);
            cleanupResources();
            throw new RuntimeException("Failed to mount F1r3flyFS", e);
        }
    }

    private void cleanupResources() {
        // destroy background tasks and queue
        if (this.fileSystem != null) {
            this.fileSystem.terminate();
            this.fileSystem = null;
        }
        if (this.finderSyncExtensionServiceServer != null) {
            this.finderSyncExtensionServiceServer.stop();
        }
    }

    public void waitOnBackgroundThread() {
        try {
            if (fileSystem != null) {
                fileSystem.waitOnBackgroundDeploy();
            }
        } catch (Throwable e) {
            LOGGER.error("Error waiting for background thread operations to complete", e);
            throw new RuntimeException("Failed to wait for background operations", e);
        }
    }

    @Override
    public void umount() {
        LOGGER.debug("Called Umounting F1r3flyFS");
        try {
            waitOnBackgroundThread();
            cleanupResources();
            super.umount();
        } catch (RuntimeException e) {
            LOGGER.error("Runtime error during unmount: {}", e.getMessage(), e);
            throw e;
        } catch (Throwable e) {
            LOGGER.error("Error unmounting F1r3flyFS: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to unmount F1r3flyFS", e);
        }
    }

    protected boolean notMounted() {
        return fileSystem == null;
    }

    private void handleExchange(String tokenFilePath) {
        LOGGER.debug("Called onExchange for path: {}", tokenFilePath);
        try {
            if (notMounted()) {
                LOGGER.warn("handleExchange - FileSystem not mounted for path: {}", tokenFilePath);
                return;
            }
            fileSystem.exchangeTokenFile(tokenFilePath);
        } catch (Throwable e) {
            LOGGER.error("Error onExchange for path: {}", tokenFilePath, e);
        }
    }

    private void handleUnlockRevDirectory(String revAddress, String privateKey) {
        LOGGER.debug("Called handleUnlockRevDirectory for revAddress: {}", revAddress);
        
        fileSystem.unlockRootDirectory(revAddress, privateKey);
        
    }

}
