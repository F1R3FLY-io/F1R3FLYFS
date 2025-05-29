package io.f1r3fly.fs.examples;

import io.f1r3fly.fs.ErrorCodes;
import io.f1r3fly.fs.FuseFillDir;
import io.f1r3fly.fs.FuseStubFS;
import io.f1r3fly.fs.SuccessCodes;
import io.f1r3fly.fs.contextmenu.FinderSyncExtensionServiceServer;
import io.f1r3fly.fs.examples.storage.DeployDispatcher;
import io.f1r3fly.fs.examples.storage.errors.*;
import io.f1r3fly.fs.examples.storage.grcp.F1r3flyApi;
import io.f1r3fly.fs.examples.storage.inmemory.InMemoryFileSystem;
import io.f1r3fly.fs.examples.storage.inmemory.common.IDirectory;
import io.f1r3fly.fs.examples.storage.inmemory.common.IPath;
import io.f1r3fly.fs.examples.storage.inmemory.deployable.InMemoryDirectory;
import io.f1r3fly.fs.examples.storage.inmemory.deployable.InMemoryFile;
import io.f1r3fly.fs.examples.storage.inmemory.deployable.RemountedDirectory;
import io.f1r3fly.fs.examples.storage.inmemory.deployable.RemountedFile;
import io.f1r3fly.fs.examples.storage.inmemory.notdeployable.TokenDirectory;
import io.f1r3fly.fs.examples.storage.inmemory.notdeployable.TokenFile;
import io.f1r3fly.fs.examples.storage.rholang.RholangExpressionConstructor;
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
import rhoapi.RhoTypes;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.function.Consumer;


public class F1r3flyFS extends FuseStubFS {

    private static final Logger LOGGER = LoggerFactory.getLogger(F1r3flyFS.class);

    private final F1r3flyApi f1R3FlyApi;
    private final String[] MOUNT_OPTIONS = {
        // refers to https://github.com/osxfuse/osxfuse/wiki/Mount-options
        "-o", "noappledouble",
        "-o", "daemon_timeout=3600", // 1 hour timeout
        "-o", "default_permissions" // permission is not supported that, this disables the permission check from Fuse side
    };
    private DeployDispatcher deployDispatcher;
    private InMemoryFileSystem fileSystem;
    private InMemoryDirectory rootDirectory;
    private FinderSyncExtensionServiceServer finderSyncExtensionServiceServer;


    public F1r3flyFS(F1r3flyApi f1R3FlyApi) {
        super(); // no need to call Fuse constructor

        this.f1R3FlyApi = f1R3FlyApi;
    }

    /**
     * Common error handling for FUSE operations
     */
    @FunctionalInterface
    private interface FuseOperation {
        int execute() throws Exception;
    }

    private int executeWithErrorHandling(String operationName, String path, FuseOperation operation) {
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
        LOGGER.debug("Called Create file {}", path);
        return executeWithErrorHandling("Create", path, () -> {
            if (!isMounted()) {
                LOGGER.warn("Create - FileSystem not mounted for path: {}", path);
                return -ErrorCodes.EIO();
            }
            // Reject creation of Apple metadata files
            if (fileSystem.isAppleMetadataFile(path)) {
                LOGGER.debug("Rejecting creation of Apple metadata file: {}", path);
                return -ErrorCodes.EACCES();
            }
            fileSystem.createFile(path, mode);
            return SuccessCodes.OK;
        });
    }

    @Override
    public int getattr(String path, FileStat stat) {
        LOGGER.trace("Called Getattr {}", path);
        return executeWithErrorHandling("Getattr", path, () -> {
            if (!isMounted()) {
                LOGGER.warn("Getattr - FileSystem not mounted for path: {}", path);
                return -ErrorCodes.ENOENT();
            }
            // Explicitly reject Apple metadata files
            if (fileSystem.isAppleMetadataFile(path)) {
                LOGGER.debug("Rejecting Apple metadata file: {}", path);
                return -ErrorCodes.ENOENT();
            }
            fileSystem.getAttributes(path, stat, getContext());
            return SuccessCodes.OK;
        });
    }

    @Override
    public int mkdir(String path, @mode_t long mode) {
        LOGGER.debug("Called Mkdir {}", path);
        return executeWithErrorHandling("Mkdir", path, () -> {
            if (!isMounted()) {
                LOGGER.warn("Mkdir - FileSystem not mounted for path: {}", path);
                return -ErrorCodes.EIO();
            }
            fileSystem.makeDirectory(path, mode);
            return SuccessCodes.OK;
        });
    }

    @Override
    public int read(String path, Pointer buf, @size_t long size, @off_t long offset, FuseFileInfo fi) {
        LOGGER.trace("Called Read file {} with buffer size {} and offset {}", path, size, offset);
        return executeWithErrorHandling("Read", path, () -> {
            if (!isMounted()) {
                LOGGER.warn("Read - FileSystem not mounted for path: {}", path);
                return -ErrorCodes.EIO();
            }
            return fileSystem.readFile(path, buf, size, offset);
        });
    }

    @Override
    public int readdir(String path, Pointer buf, FuseFillDir filter, @off_t long offset, FuseFileInfo fi) {
        LOGGER.debug("Called Readdir {}", path);
        return executeWithErrorHandling("Readdir", path, () -> {
            if (!isMounted()) {
                LOGGER.warn("Readdir - FileSystem not mounted for path: {}", path);
                return -ErrorCodes.EIO();
            }
            fileSystem.readDirectory(path, buf, filter);
            return SuccessCodes.OK;
        });
    }

    @Override
    public int statfs(String path, Statvfs stbuf) {
        LOGGER.trace("Called Statfs {}", path);
        return executeWithErrorHandling("Statfs", path, () -> {
            if (!isMounted()) {
                LOGGER.warn("Statfs - FileSystem not mounted for path: {}", path);
                return -ErrorCodes.EIO();
            }
            fileSystem.getFileSystemStats(path, stbuf);
            return super.statfs(path, stbuf);
        });
    }

    @Override
    public int rename(String path, String newName) {
        LOGGER.debug("Called Rename {} to {}", path, newName);
        return executeWithErrorHandling("Rename", path, () -> {
            if (!isMounted()) {
                LOGGER.warn("Rename - FileSystem not mounted for path: {}", path);
                return -ErrorCodes.EIO();
            }
            fileSystem.renameFile(path, newName);
            return SuccessCodes.OK;
        });
    }

    @Override
    public int rmdir(String path) {
        LOGGER.debug("Called Rmdir {}", path);
        return executeWithErrorHandling("Rmdir", path, () -> {
            if (!isMounted()) {
                LOGGER.warn("Rmdir - FileSystem not mounted for path: {}", path);
                return -ErrorCodes.EIO();
            }
            fileSystem.removeDirectory(path);
            return SuccessCodes.OK;
        });
    }

    @Override
    public int truncate(String path, long offset) {
        LOGGER.debug("Called Truncate file {}", path);
        return executeWithErrorHandling("Truncate", path, () -> {
            if (!isMounted()) {
                LOGGER.warn("Truncate - FileSystem not mounted for path: {}", path);
                return -ErrorCodes.EIO();
            }
            fileSystem.truncateFile(path, offset);
            return SuccessCodes.OK;
        });
    }

    @Override
    public int unlink(String path) {
        LOGGER.debug("Called Unlink {}", path);
        return executeWithErrorHandling("Unlink", path, () -> {
            if (!isMounted()) {
                LOGGER.warn("Unlink - FileSystem not mounted for path: {}", path);
                return -ErrorCodes.EIO();
            }
            fileSystem.unlinkFile(path);
            return SuccessCodes.OK;
        });
    }

    @Override
    public int open(String path, FuseFileInfo fi) {
        LOGGER.debug("Called Open file {}", path);
        return executeWithErrorHandling("Open", path, () -> {
            if (!isMounted()) {
                LOGGER.warn("Open - FileSystem not mounted for path: {}", path);
                return -ErrorCodes.EIO();
            }
            // Reject opening Apple metadata files
            if (fileSystem.isAppleMetadataFile(path)) {
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
        LOGGER.trace("Called Write file {} with buffer size {} and offset {}", path, size, offset);
        return executeWithErrorHandling("Write", path, () -> {
            if (!isMounted()) {
                LOGGER.warn("Write - FileSystem not mounted for path: {}", path);
                return -ErrorCodes.EIO();
            }
            return fileSystem.writeFile(path, buf, size, offset);
        });
    }

    @Override
    public int flush(String path, FuseFileInfo fi) {
        LOGGER.debug("Called Flush file {}", path);
        return executeWithErrorHandling("Flush", path, () -> {
            if (!isMounted()) {
                LOGGER.warn("Flush - FileSystem not mounted for path: {}", path);
                return -ErrorCodes.EIO();
            }
            fileSystem.flushFile(path);
            return SuccessCodes.OK;
        });
    }


    public void remount(String mountName, Path mountPoint) throws PathIsNotADirectory, NoDataByPath {
        remount(mountName, mountPoint, false, false, new String[]{});
    }

    public void remount(String mountName, Path mountPoint, boolean blocking, boolean debug, String[] fuseOpts) throws PathIsNotADirectory, NoDataByPath {
        LOGGER.debug("Called remount F1r3flyFS with mount name {}, mount point {}, blocking {}, debug, {}, fuse opts {}",
            mountName, mountPoint, blocking, debug, Arrays.toString(fuseOpts));
        try {
            this.mountName = mountName;

            this.deployDispatcher = new DeployDispatcher(f1R3FlyApi);
            this.fileSystem = new InMemoryFileSystem(null, this.deployDispatcher, this.f1R3FlyApi, this.mountName);

            IPath root = fileSystem.fetchDirectoryFromShard(this.mountName, "", null);

            if (root instanceof InMemoryDirectory) {
                this.rootDirectory = (InMemoryDirectory) root;
                this.fileSystem.setRootDirectory(this.rootDirectory);
            } else {
                throw new PathIsNotADirectory("Root path " + root.getAbsolutePath() + " is not a directory");
            }

            F1r3flyFSTokenization.initializeTokenDirectory(rootDirectory, this.deployDispatcher);
            deployDispatcher.startBackgroundDeploy();

            super.mount(mountPoint, blocking, debug, fuseOpts);

        } catch (NoDataByPath | PathIsNotADirectory e) {
            LOGGER.error("Error re-mounting F1r3flyFS: {}", e.getMessage(), e);
            cleanupResources();
            throw e;
        } catch (Throwable e) {
            LOGGER.error("Unexpected error re-mounting F1r3flyFS on {}: {}", mountPoint, e.getMessage(), e);
            cleanupResources();
            throw new RuntimeException("Failed to remount F1r3flyFS", e);
        }
    }

    @Override
    public void mount(Path mountPoint, boolean blocking, boolean debug, String[] fuseOpts) {
        LOGGER.debug("Called Mounting F1r3flyFS on {} with opts {}", mountPoint, Arrays.toString(fuseOpts));

        try {
            generateMountName();

            // combine fuseOpts and MOUNT_OPTIONS
            String[] allFuseOpts = Arrays.copyOf(fuseOpts, fuseOpts.length + MOUNT_OPTIONS.length);
            System.arraycopy(MOUNT_OPTIONS, 0, allFuseOpts, fuseOpts.length, MOUNT_OPTIONS.length);

            this.deployDispatcher = new DeployDispatcher(f1R3FlyApi);
            this.rootDirectory = new InMemoryDirectory(this.mountName, "", null, this.deployDispatcher);
            this.fileSystem = new InMemoryFileSystem(this.rootDirectory, this.deployDispatcher, this.f1R3FlyApi, this.mountName);
            this.finderSyncExtensionServiceServer = new FinderSyncExtensionServiceServer(
                this::handleExchange, 54000
            );

            deployDispatcher.startBackgroundDeploy();

            F1r3flyFSTokenization.initializeTokenDirectory(rootDirectory, this.deployDispatcher);
            waitOnBackgroundThread();

            finderSyncExtensionServiceServer.start();

            super.mount(mountPoint, blocking, debug, allFuseOpts);

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
        if (this.deployDispatcher != null) {
            this.deployDispatcher.destroy();
            this.deployDispatcher = null;
            this.rootDirectory = null;
            this.fileSystem = null;
        }
        if (this.finderSyncExtensionServiceServer != null) {
            this.finderSyncExtensionServiceServer.stop();
        }
    }

    public void waitOnBackgroundThread() {
        LOGGER.debug("Called waitOnBackgroundThread");
        try {
            if (this.deployDispatcher != null) {
                this.deployDispatcher.waitOnEmptyQueue();
            }
            LOGGER.debug("waitOnBackgroundThread completed");
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

    // public because of this method is used in tests
    public String prependMountName(String path) {
        if (!isMounted()) {
            LOGGER.warn("prependMountName - FileSystem not mounted, returning original path: {}", path);
            return path;
        }
        return fileSystem.prependMountName(path);
    }

    public String getMountName() {
        return this.mountName;
    }

    protected void generateMountName() {
        this.mountName = "f1r3flyfs" + ThreadLocalRandom.current().nextInt();
    }

    protected boolean isMounted() {
        return fileSystem != null && fileSystem.isMounted();
    }

    private void handleExchange(String tokenFilePath) {
        LOGGER.debug("Called onExchange for path: {}", tokenFilePath);
        try {
            if (!isMounted()) {
                LOGGER.warn("handleExchange - FileSystem not mounted for path: {}", tokenFilePath);
                return;
            }
            String normilizedPath = tokenFilePath.substring(this.mountPoint.toFile().getAbsolutePath().length());
            IPath p = fileSystem.getPath(normilizedPath);
            if (p instanceof TokenFile) {
                TokenFile tokenFile = (TokenFile) p;
                ((TokenDirectory) p.getParent()).exchange(tokenFile);
            }
        } catch (Throwable e) {
            LOGGER.error("Error onExchange for path: {}", tokenFilePath, e);
        }
    }

}
