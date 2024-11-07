package io.f1r3fly.fs.examples;

import io.f1r3fly.fs.*;
import io.f1r3fly.fs.examples.storage.DeployDispatcher;
import io.f1r3fly.fs.examples.storage.errors.NoDataByPath;
import io.f1r3fly.fs.examples.storage.errors.PathIsNotADirectory;
import io.f1r3fly.fs.examples.storage.grcp.F1r3flyApi;
import io.f1r3fly.fs.examples.storage.inmemory.MemoryDirectory;
import io.f1r3fly.fs.examples.storage.inmemory.MemoryFile;
import io.f1r3fly.fs.examples.storage.inmemory.MemoryPath;
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
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;


public class F1r3flyFS extends FuseStubFS {

    private static final Logger LOGGER = LoggerFactory.getLogger(F1r3flyFS.class);

    private final F1r3flyApi f1R3FlyApi;
    private DeployDispatcher deployDispatcher;
    private MemoryDirectory rootDirectory;

    private final String[] MOUNT_OPTIONS = {
        // refers to https://github.com/osxfuse/osxfuse/wiki/Mount-options
        "-o", "noappledouble",
        "-o", "daemon_timeout=3600", // 1 hour timeout
        "-o", "default_permissions" // permission is not supported that, this disables the permission check from Fuse side
    };


    public F1r3flyFS(F1r3flyApi f1R3FlyApi) {
        super(); // no need to call Fuse constructor

        this.f1R3FlyApi = f1R3FlyApi;
    }

    @Override
    public int create(String path, @mode_t long mode, FuseFileInfo fi) {
        LOGGER.debug("Called Create file {}", path);
        try {
            if (getPath(path) != null) {
                return -ErrorCodes.EEXIST();
            }
            MemoryPath parent = getParentPath(path);
            if (parent instanceof MemoryDirectory) {
                ((MemoryDirectory) parent).mkfile(getLastComponent(path), true);
                return SuccessCodes.OK;
            }
            return -ErrorCodes.ENOENT();
        } catch (Throwable e) {
            LOGGER.error("Error creating file {}", path, e);
            return -ErrorCodes.EIO();
        }
    }


    @Override
    public int getattr(String path, FileStat stat) {
        LOGGER.debug("Called Getattr {}", path);
        try {
            if (!isMounted()) {
                return -ErrorCodes.ENOENT();
            }

            MemoryPath p = getPath(path);
            if (p != null) {
                p.getattr(stat, getContext());
                return SuccessCodes.OK;
            }
            return -ErrorCodes.ENOENT();
        } catch (Throwable e) {
            LOGGER.error("Error getting attributes for {}", path, e);
            return -ErrorCodes.EIO();
        }
    }

    private String getLastComponent(String path) {
        while (path.substring(path.length() - 1).equals("/")) {
            path = path.substring(0, path.length() - 1);
        }
        if (path.isEmpty()) {
            return "";
        }
        return path.substring(path.lastIndexOf("/") + 1);
    }

    private MemoryPath getParentPath(String path) {
        return rootDirectory.find(path.substring(0, path.lastIndexOf("/")));
    }

    private MemoryPath getPath(String path) {
        return rootDirectory.find(path);
    }


    @Override
    public int mkdir(String path, @mode_t long mode) {
        try {
            LOGGER.debug("Called Mkdir {}", path);
            if (getPath(path) != null) {
                return -ErrorCodes.EEXIST();
            }
            MemoryPath parent = getParentPath(path);
            if (parent instanceof MemoryDirectory) {
                ((MemoryDirectory) parent).mkdir(getLastComponent(path), true);
                return SuccessCodes.OK;
            }
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
            MemoryPath p = getPath(path);
            if (p == null) {
                return -ErrorCodes.ENOENT();
            }
            if (!(p instanceof MemoryFile)) {
                return -ErrorCodes.EISDIR();
            }
            return ((MemoryFile) p).read(buf, size, offset);
        } catch (Throwable e) {
            LOGGER.warn("Error reading file {}", path, e);
            return -ErrorCodes.EIO();
        }
    }

    @Override
    public int readdir(String path, Pointer buf, FuseFillDir filter, @off_t long offset, FuseFileInfo fi) {
        LOGGER.debug("Called Readdir {}", path);
        try {
            MemoryPath p = getPath(path);
            if (p == null) {
                return -ErrorCodes.ENOENT();
            }
            if (!(p instanceof MemoryDirectory)) {
                return -ErrorCodes.ENOTDIR();
            }
            filter.apply(buf, ".", null, 0);
            filter.apply(buf, "..", null, 0);
            ((MemoryDirectory) p).read(buf, filter);
            return SuccessCodes.OK;
        } catch (Throwable e) {
            LOGGER.error("Error reading directory {}", path, e);
            return -ErrorCodes.EIO();
        }
    }


    @Override
    public int statfs(String path, Statvfs stbuf) {
        LOGGER.debug("Called Statfs {}", path);
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
            LOGGER.error("Error statfs", e);
            return -ErrorCodes.EIO();
        }
    }

    @Override
    public int rename(String path, String newName) {
        LOGGER.debug("Called Rename {} to {}", path, newName);
        try {
            MemoryPath p = getPath(path);
            if (p == null) {
                return -ErrorCodes.ENOENT();
            }
            MemoryPath newParent = getParentPath(newName);
            if (newParent == null) {
                return -ErrorCodes.ENOENT();
            }
            if (!(newParent instanceof MemoryDirectory)) {
                return -ErrorCodes.ENOTDIR();
            }
//            p.delete();
            p.rename(newName.substring(newName.lastIndexOf(PathUtils.getPathDelimiterBasedOnOS())), (MemoryDirectory) newParent, true);
            ((MemoryDirectory) newParent).add(p, true);

            if (p instanceof MemoryFile)
                ((MemoryFile) p).onChange();

            return SuccessCodes.OK;
        } catch (Throwable e) {
            LOGGER.error("Error renaming file {}", path, e);
            return -ErrorCodes.EIO();
        }
    }

    @Override
    public int rmdir(String path) {
        LOGGER.debug("Called Rmdir {}", path);
        try {
            MemoryPath p = getPath(path);
            if (p == null) {
                return -ErrorCodes.ENOENT();
            }
            if (!(p instanceof MemoryDirectory)) {
                return -ErrorCodes.ENOTDIR();
            }
            if (!((MemoryDirectory) p).isEmpty()) {
                return -ErrorCodes.ENOTEMPTY();
            }
            p.delete();
            return SuccessCodes.OK;
        } catch (Throwable e) {
            LOGGER.error("Error removing directory {}", path, e);
            return -ErrorCodes.EIO();
        }
    }

    @Override
    public int truncate(String path, long offset) {
        LOGGER.debug("Called Truncate file {}", path);
        try {
            MemoryPath p = getPath(path);
            if (p == null) {
                return -ErrorCodes.ENOENT();
            }
            if (!(p instanceof MemoryFile)) {
                return -ErrorCodes.EISDIR();
            }
            ((MemoryFile) p).truncate(offset);
            return SuccessCodes.OK;
        } catch (Throwable e) {
            LOGGER.error("Error truncating file {}", path, e);
            return -ErrorCodes.EIO();
        }
    }

    @Override
    public int unlink(String path) {
        LOGGER.debug("Called Unlink {}", path);
        try {
            MemoryPath p = getPath(path);
            if (p == null) {
                return -ErrorCodes.ENOENT();
            }
            p.delete();
            return SuccessCodes.OK;
        } catch (Throwable e) {
            LOGGER.error("Error unlinking file {}", path, e);
            return -ErrorCodes.EIO();
        }
    }

    @Override
    public int open(String path, FuseFileInfo fi) {
        LOGGER.debug("Called Open file {}", path);
        try {
            MemoryPath p = getPath(path);
            if (p == null) {
                return -ErrorCodes.ENOENT();
            }
            if (p instanceof MemoryFile) {
                ((MemoryFile) p).open();
                LOGGER.debug("Opened file {}", path);
            }
            return SuccessCodes.OK;
        } catch (Throwable e) {
            LOGGER.warn("Error opening file", e);
            return -ErrorCodes.EIO();
        }
    }

    @Override
    public int write(String path, Pointer buf, @size_t long size, @off_t long offset, FuseFileInfo fi) {
        LOGGER.trace("Called Write file {} with buffer size {} and offset {}", path, size, offset);
        try {
            MemoryPath p = getPath(path);
            if (p == null) {
                return -ErrorCodes.ENOENT();
            }
            if (!(p instanceof MemoryFile)) {
                return -ErrorCodes.EISDIR();
            }
            return ((MemoryFile) p).write(buf, size, offset);
        } catch (Throwable e) {
            LOGGER.warn("Error writing to file", e);
            return -ErrorCodes.EIO();
        }
    }

    @Override
    public int flush(String path, FuseFileInfo fi) {
        LOGGER.debug("Called Flush file {}", path);
        try {
            MemoryPath p = getPath(path);
            if (p == null) {
                return -ErrorCodes.ENOENT();
            }
            if (p instanceof MemoryFile) {
                ((MemoryFile) p).close();
                ((MemoryFile) p).onChange();
            }
            return SuccessCodes.OK;
        } catch (Throwable e) {
            LOGGER.warn("Error closing file", e);
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

            MemoryPath root = fetchDirectoryFromShard(this.mountName, "", null);

            if (root instanceof MemoryDirectory) {
                this.rootDirectory = (MemoryDirectory) root;
            } else {
                throw new PathIsNotADirectory("Root path " + root.getAbsolutePath() + " is not a directory");
            }

            super.mount(mountPoint, blocking, debug, fuseOpts);

            deployDispatcher.startBackgroundDeploy();
        } catch (Throwable e) {
            LOGGER.error("Error re-mounting F1r3flyFS", e);

            // destroy background tasks and queue
            if (this.deployDispatcher != null) {
                this.deployDispatcher.destroy();
                this.deployDispatcher = null;
                this.rootDirectory = null;
            }

            throw e;
        }
    }

    private MemoryPath fetchDirectoryFromShard(String absolutePath, String name, MemoryDirectory parent) throws NoDataByPath {
        try {
            List<RhoTypes.Par> pars = f1R3FlyApi.findDataByName(absolutePath);

            RholangExpressionConstructor.ChannelData fileOrDir = RholangExpressionConstructor.parseChannelData(pars);

            if (fileOrDir.isDir()) {
                MemoryDirectory dir =
                    new MemoryDirectory(this.mountName, name, parent, this.deployDispatcher, false);

                fileOrDir.children().forEach(childName -> {
                    try {
                        MemoryPath child = fetchDirectoryFromShard(absolutePath + PathUtils.getPathDelimiterBasedOnOS() + childName, childName, dir);
                        dir.add(child, false);
                    } catch (NoDataByPath e) {
                        LOGGER.error("Error fetching child directory from shard", e);
                        // skip for now
                    }
                });

                return dir;

            } else {
                MemoryFile file = new MemoryFile(this.mountName, PathUtils.getFileName(absolutePath), parent, this.deployDispatcher, false);
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
            throw e;
        } catch (Throwable e) {
            LOGGER.error("Error fetching directory from shard", e);
            throw new RuntimeException(e);
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
            this.rootDirectory = new MemoryDirectory(this.mountName, "", this.deployDispatcher, true);

            deployDispatcher.startBackgroundDeploy();

            super.mount(mountPoint, blocking, debug, fuseOpts);

        } catch (Throwable e) {
            LOGGER.error("Error mounting F1r3flyFS", e);

            // destroy background tasks and queue
            if (this.deployDispatcher != null) {
                this.deployDispatcher.destroy();
                this.deployDispatcher = null;
                this.rootDirectory = null;
            }

            throw e;
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
            LOGGER.error("Error destroying F1r3flyFS", e);
            throw e;
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
        } catch (Throwable e) {
            LOGGER.error("Error unmounting F1r3flyFS", e);
            throw e;
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
}
