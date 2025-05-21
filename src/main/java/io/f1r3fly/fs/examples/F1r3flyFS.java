package io.f1r3fly.fs.examples;

import io.f1r3fly.fs.ErrorCodes;
import io.f1r3fly.fs.FuseFillDir;
import io.f1r3fly.fs.FuseStubFS;
import io.f1r3fly.fs.SuccessCodes;
import io.f1r3fly.fs.contextmenu.ContextManuServiceServer;
import io.f1r3fly.fs.examples.storage.DeployDispatcher;
import io.f1r3fly.fs.examples.storage.errors.*;
import io.f1r3fly.fs.examples.storage.grcp.F1r3flyApi;
import io.f1r3fly.fs.examples.storage.inmemory.common.IDirectory;
import io.f1r3fly.fs.examples.storage.inmemory.common.IFile;
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
    private InMemoryDirectory rootDirectory;
    private ContextManuServiceServer contextManuServiceServer;


    public F1r3flyFS(F1r3flyApi f1R3FlyApi) {
        super(); // no need to call Fuse constructor

        this.f1R3FlyApi = f1R3FlyApi;
    }

    @Override
    public int create(String path, @mode_t long mode, FuseFileInfo fi) {
        LOGGER.debug("Called Create file {}", path);
        try {
            // Reject creation of Apple metadata files
            if (isAppleMetadataFile(path)) {
                LOGGER.debug("Rejecting creation of Apple metadata file: {}", path);
                return -ErrorCodes.EACCES();
            }

            IPath maybeExist = findPath(path);

            if (maybeExist != null) {
                // already exists
                return -ErrorCodes.EEXIST();
            }

            IDirectory parent = getParentPath(path); // fail if not found

            parent.mkfile(getLastComponent(path));

            return SuccessCodes.OK;

        } catch (PathNotFound e) {
            LOGGER.warn("Parent path not found for {}", path, e);
            return -ErrorCodes.ENOENT();
        } catch (Throwable e) {
            LOGGER.error("Error creating file {}", path, e);
            return -ErrorCodes.EIO();
        }
    }


    @Override
    public int getattr(String path, FileStat stat) {
        LOGGER.trace("Called Getattr {}", path);
        try {
            if (!isMounted()) {
                return -ErrorCodes.ENOENT();
            }

            // Explicitly reject Apple metadata files
            if (isAppleMetadataFile(path)) {
                LOGGER.debug("Rejecting Apple metadata file: {}", path);
                return -ErrorCodes.ENOENT();
            }

            IPath p = findPath(path);
            if (p != null) {
                p.getAttr(stat, getContext());
                return SuccessCodes.OK;
            }
            return -ErrorCodes.ENOENT();
        } catch (Throwable e) {
            LOGGER.error("Error getting attributes for {}", path, e);
            return -ErrorCodes.EIO();
        }
    }

    private String getLastComponent(String path) {
        while (path.endsWith(PathUtils.getPathDelimiterBasedOnOS())) {
            path = path.substring(0, path.length() - 1);
        }
        if (path.isEmpty()) {
            return "";
        }
        return path.substring(path.lastIndexOf(PathUtils.getPathDelimiterBasedOnOS()) + 1);
    }

    private IDirectory getParentPath(String path) throws PathNotFound {
        String parentPath = path.substring(0, path.lastIndexOf("/"));

        IPath parent = getPath(parentPath);

        if (!(parent instanceof IDirectory)) {
            throw new IllegalArgumentException("Parent path is not a directory: " + parentPath);
        }
        return (IDirectory) parent;
    }

    private IPath findPath(String path) {
        return rootDirectory.find(path);
    }
    private IPath getPath(String path) throws PathNotFound {
        IPath element = findPath(path);

        if (element == null) {
            throw new PathNotFound(path);
        }

        return element;
    }

    private IDirectory getDirectory(String path) throws PathNotFound, PathIsNotADirectory {
        IPath element = findPath(path);

        if (element == null) {
            throw new PathNotFound(path);
        }

        if (!(element instanceof IDirectory)) {
            throw new PathIsNotADirectory(path);
        }

        return (IDirectory) element;
    }

    private IFile getFile(String path) throws PathNotFound, PathIsNotAFile {
        IPath element = findPath(path);

        if (element == null) {
            throw new PathNotFound(path);
        }

        if (!(element instanceof IFile)) {
            throw new PathIsNotAFile(path);
        }

        return (IFile) element;
    }


    @Override
    public int mkdir(String path, @mode_t long mode) {
        try {
            LOGGER.debug("Called Mkdir {}", path);

            IPath maybeExist = findPath(path);
            if (maybeExist != null) {
                // already exists
                return -ErrorCodes.EEXIST();
            }

            IDirectory parent = getParentPath(path);
            parent.mkdir(getLastComponent(path));

            return SuccessCodes.OK;
        } catch (OperationNotPermitted e) {
            LOGGER.warn("Mkdir not permitted {}", path, e);
            return -ErrorCodes.EPERM();
        } catch (PathNotFound e) {
            LOGGER.warn("Path not found {}", path, e);
            return -ErrorCodes.ENOENT();
        } catch (Throwable e) {
            LOGGER.error("Error creating directory {}", path, e);
            return -ErrorCodes.EIO();
        }
    }


    @Override
    public int read(String path, Pointer buf, @size_t long size, @off_t long offset, FuseFileInfo fi) {
        LOGGER.trace("Called Read file {} with buffer size {} and offset {}", path, size, offset);
        try {
            IFile file = getFile(path);

            return file.read(buf, size, offset);
        } catch (PathIsNotAFile e) {
            LOGGER.warn("Path {} is not a file", path, e);
            return -ErrorCodes.EISDIR();
        } catch (PathNotFound e) {
            LOGGER.warn("Path {} not found", path, e);
            return -ErrorCodes.ENOENT();
        } catch (Throwable e) {
            LOGGER.warn("Error reading file {}", path, e);
            return -ErrorCodes.EIO();
        }
    }

    @Override
    public int readdir(String path, Pointer buf, FuseFillDir filter, @off_t long offset, FuseFileInfo fi) {
        LOGGER.debug("Called Readdir {}", path);
        try {
            IDirectory p = getDirectory(path);
            p.read(buf, filter);

            return SuccessCodes.OK;
        } catch (PathIsNotADirectory e) {
            LOGGER.warn("Path {} is not a directory", path, e);
            return -ErrorCodes.ENOTDIR();
        } catch (PathNotFound e) {
            LOGGER.warn("Path {} not found", path, e);
            return -ErrorCodes.ENOENT();
        } catch (Throwable e) {
            LOGGER.error("Error reading directory {}", path, e);
            return -ErrorCodes.EIO();
        }
    }


    @Override
    public int statfs(String path, Statvfs stbuf) {
        LOGGER.trace("Called Statfs {}", path);
        try {
            // UI checks free space before writing a file
            // letting Fuse know that we have 100GB free space
            if ("/".equals(path)) {
                int BLOCKSIZE = 4096;
                int FUSE_NAME_MAX = 255;

                long totalSpace = 100L * 1024 * 1024 * 1024;
                long UsableSpace = totalSpace; // TODO: fix it later
                long tBlocks = totalSpace / BLOCKSIZE;
                long aBlocks = UsableSpace / BLOCKSIZE;
                stbuf.f_bsize.set(BLOCKSIZE);
                stbuf.f_frsize.set(BLOCKSIZE);
                stbuf.f_blocks.set(tBlocks);
                stbuf.f_bavail.set(aBlocks);
                stbuf.f_bfree.set(aBlocks);
                stbuf.f_namemax.set(FUSE_NAME_MAX);
            }

            return super.statfs(path, stbuf);
        } catch (Throwable e) {
            LOGGER.error("Error getting filesystem stats for path {}", path, e);
            return -ErrorCodes.EIO();
        }
    }

    @Override
    public int rename(String path, String newName) {
        LOGGER.debug("Called Rename {} to {}", path, newName);
        try {
            IPath p = getPath(path); // fail if not found
            IDirectory newParent = getParentPath(newName); // fail if not found

            IDirectory oldParent = p.getParent();
            p.rename(newName.substring(newName.lastIndexOf(PathUtils.getPathDelimiterBasedOnOS()) + 1), newParent);

            if (oldParent != newParent) {
                newParent.addChild(p);
                oldParent.deleteChild(p);
            } else {
                newParent.addChild(p); // re-add to force update children list at the shard
            }


            if (p instanceof InMemoryFile)
                ((InMemoryFile) p).onChange();

            return SuccessCodes.OK;
        } catch (PathNotFound e) {
            LOGGER.warn("Path not found during rename: {}", e.getMessage());
            return -ErrorCodes.ENOENT();
        } catch (Throwable e) {
            LOGGER.error("Error renaming file {} to {}", path, newName, e);
            return -ErrorCodes.EIO();
        }
    }

    @Override
    public int rmdir(String path) {
        LOGGER.debug("Called Rmdir {}", path);
        try {
            IDirectory p = getDirectory(path);
            if (!p.isEmpty()) {
                LOGGER.debug("Directory {} is not empty", path);
                return -ErrorCodes.ENOTEMPTY();
            }
            p.delete();
            IDirectory parent = p.getParent();
            if (parent != null) {
                parent.deleteChild(p);
            }
            return SuccessCodes.OK;
        } catch (OperationNotPermitted e) {
            LOGGER.warn("rmdir not permitted {}", path, e);
            return -ErrorCodes.EPERM();
        } catch (PathNotFound e) {
            LOGGER.warn("Path {} not found", path, e);
            return -ErrorCodes.ENOENT();
        } catch (Throwable e) {
            LOGGER.error("Error removing directory {}", path, e);
            return -ErrorCodes.EIO();
        }
    }

    @Override
    public int truncate(String path, long offset) {
        LOGGER.debug("Called Truncate file {}", path);
        try {
            IFile p = getFile(path);
            p.truncate(offset);
            return SuccessCodes.OK;
        } catch (PathIsNotAFile e) {
            LOGGER.warn("Path {} is not a file", path, e);
            return -ErrorCodes.EISDIR();
        } catch (PathNotFound e) {
            LOGGER.warn("Path {} not found", path, e);
            return -ErrorCodes.ENOENT();
        } catch (Throwable e) {
            LOGGER.error("Error truncating file {}", path, e);
            return -ErrorCodes.EIO();
        }
    }

    @Override
    public int unlink(String path) {
        LOGGER.debug("Called Unlink {}", path);
        try {
            IPath p = getPath(path);
            p.delete();
            IDirectory parent = p.getParent();
            if (parent != null) {
                parent.deleteChild(p);
            }
            return SuccessCodes.OK;
        } catch (OperationNotPermitted e) {
            LOGGER.warn("Unlink not permitted {}", path, e);
            return -ErrorCodes.EPERM();
        } catch (PathNotFound e) {
            LOGGER.warn("Path {} not found", path, e);
            return -ErrorCodes.ENOENT();
        } catch (Throwable e) {
            LOGGER.error("Error unlinking file {}", path, e);
            return -ErrorCodes.EIO();
        }
    }

    @Override
    public int open(String path, FuseFileInfo fi) {
        LOGGER.debug("Called Open file {}", path);
        try {
            // Reject opening Apple metadata files
            if (isAppleMetadataFile(path)) {
                LOGGER.debug("Rejecting open of Apple metadata file: {}", path);
                return -ErrorCodes.ENOENT();
            }
            
            IFile p = getFile(path);
            p.open();
            LOGGER.debug("Opened file {}", path);
            return SuccessCodes.OK;
        } catch (PathNotFound e) {
            LOGGER.warn("Path {} not found", path, e);
            return -ErrorCodes.ENOENT();
        } catch (PathIsNotAFile e) {
            LOGGER.warn("Path {} is not a file", path, e);
            return -ErrorCodes.EISDIR();
        } catch (Throwable e) {
            LOGGER.warn("Error opening file", e);
            return -ErrorCodes.EIO();
        }
    }

    @Override
    public int write(String path, Pointer buf, @size_t long size, @off_t long offset, FuseFileInfo fi) {
        LOGGER.trace("Called Write file {} with buffer size {} and offset {}", path, size, offset);
        try {
            IFile file = getFile(path);
            return file.write(buf, size, offset);
        } catch (PathIsNotAFile e) {
            LOGGER.warn("Path {} is not a file", path, e);
            return -ErrorCodes.EISDIR();
        } catch (PathNotFound e) {
            LOGGER.warn("Path {} not found", path, e);
            return -ErrorCodes.ENOENT();
        } catch (Throwable e) {
            LOGGER.error("Error writing to file {}", path, e);
            return -ErrorCodes.EIO();
        }
    }

    @Override
    public int flush(String path, FuseFileInfo fi) {
        LOGGER.debug("Called Flush file {}", path);
        try {
            IFile file = getFile(path);

            file.close();

            return SuccessCodes.OK;
        } catch (PathIsNotAFile e) {
            LOGGER.warn("Path {} is not a file", path, e);
            return -ErrorCodes.EISDIR();
        } catch (PathNotFound e) {
            LOGGER.warn("Path {} not found", path, e);
            return -ErrorCodes.ENOENT();
        } catch (Throwable e) {
            LOGGER.error("Error flushing file {}", path, e);
            return -ErrorCodes.EIO();
        }
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

            IPath root = fetchDirectoryFromShard(this.mountName, "", null);

            if (root instanceof InMemoryDirectory) {
                this.rootDirectory = (InMemoryDirectory) root;
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

    private IPath fetchDirectoryFromShard(String absolutePath, String name, InMemoryDirectory parent) throws NoDataByPath {
        try {
            List<RhoTypes.Par> pars = f1R3FlyApi.findDataByName(absolutePath);

            RholangExpressionConstructor.ChannelData fileOrDir = RholangExpressionConstructor.parseChannelData(pars);

            if (fileOrDir.isDir()) {
                RemountedDirectory dir =
                    new RemountedDirectory(this.mountName, name, parent, this.deployDispatcher);

                Set<IPath> children = fileOrDir.children().stream().map((childName) -> {
                    try {
                        return fetchDirectoryFromShard(absolutePath + PathUtils.getPathDelimiterBasedOnOS() + childName, childName, dir);
                    } catch (NoDataByPath e) {
                        LOGGER.error("Error fetching child directory from shard for path: {}",
                            absolutePath + PathUtils.getPathDelimiterBasedOnOS() + childName, e);
                        // skip for now
                        return null;
                    }
                }).filter(Objects::nonNull).collect(Collectors.toSet());

                dir.setChildren(children);

                return dir;

            } else {
                RemountedFile file = new RemountedFile(this.mountName, PathUtils.getFileName(absolutePath), parent, this.deployDispatcher);
                long offset = 0;
                offset = file.initFromBytes(fileOrDir.firstChunk(), offset);

                if (!fileOrDir.otherChunks().isEmpty()) {
                    Set<Integer> chunkNumbers = fileOrDir.otherChunks().keySet();
                    Integer[] sortedChunkNumbers = chunkNumbers.stream().sorted().toArray(Integer[]::new);

                    for (Integer chunkNumber : sortedChunkNumbers) {
                        String subChannel = fileOrDir.otherChunks().get(chunkNumber);
                        List<RhoTypes.Par> subChannelPars = f1R3FlyApi.findDataByName(subChannel);
                        byte[] data = RholangExpressionConstructor.parseBytes(subChannelPars);

                        offset = offset + file.initFromBytes(data, offset);
                    }
                }

                file.initSubChannels(fileOrDir.otherChunks());

                return file;
            }

        } catch (NoDataByPath e) {
            LOGGER.warn("No data found for path: {}", absolutePath, e);
            throw e;
        } catch (Throwable e) {
            LOGGER.error("Error fetching directory from shard for path: {}", absolutePath, e);
            throw new RuntimeException("Failed to fetch directory data for " + absolutePath, e);
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
            this.contextManuServiceServer = new ContextManuServiceServer(
                this::handleExchange, 54000
            );

            deployDispatcher.startBackgroundDeploy();

            F1r3flyFSTokenization.initializeTokenDirectory(rootDirectory, this.deployDispatcher);
            waitOnBackgroundThread();

            contextManuServiceServer.start();

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
            this.contextManuServiceServer.stop();
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
            if (this.deployDispatcher != null) {
                this.deployDispatcher.destroy();
            }
            this.deployDispatcher = null;
            this.rootDirectory = null;
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
        // example: f1r3flyfs-123123123/path-to-file
        return this.mountName + path;
    }

    public String getMountName() {
        return this.mountName;
    }

    protected void generateMountName() {
        this.mountName = "f1r3flyfs" + ThreadLocalRandom.current().nextInt();
    }

    protected boolean isMounted() {
        return this.rootDirectory != null;
    }

    private boolean isAppleMetadataFile(String path) {
        return path.contains(".DS_Store") || path.contains("._.");
    }

    private void handleExchange(String tokenFilePath) {
        LOGGER.debug("Called onExchange for path: {}", tokenFilePath);
        try {
            String normilizedPath = tokenFilePath.substring(this.mountPoint.toFile().getAbsolutePath().length());
            IPath p = getPath(normilizedPath);
            if (p instanceof TokenFile) {
                TokenFile tokenFile = (TokenFile) p;
                ((TokenDirectory) p.getParent()).exchange(tokenFile);
            }
        } catch (Throwable e) {
            LOGGER.error("Error onExchange for path: {}", tokenFilePath, e);
        }
    }

}
