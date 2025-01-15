package io.f1r3fly.fs.examples;

import casper.ExternalCommunicationServiceCommon;
import casper.v1.ExternalCommunicationServiceV1;
import io.f1r3fly.fs.*;
import io.f1r3fly.fs.examples.storage.background.NotificationsSubscriber;
import io.f1r3fly.fs.examples.storage.errors.NoDataByPath;
import io.f1r3fly.fs.examples.storage.errors.PathIsNotADirectory;
import io.f1r3fly.fs.examples.storage.grcp.listener.F1r3flyDriveServer;
import io.f1r3fly.fs.examples.storage.grcp.listener.NotificationConstructor;
import io.f1r3fly.fs.examples.storage.grcp.listener.UpdateNotificationHandler;
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
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rhoapi.RhoTypes;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;


public class F1r3flyFS extends FuseStubFS {

    protected final Logger log;

    private MemoryDirectory rootDirectory;
    private F1r3flyDriveServer grpcServer;

    private final String[] MOUNT_OPTIONS = {
        // refers to https://github.com/osxfuse/osxfuse/wiki/Mount-options
        "-o", "noappledouble",
        "-o", "daemon_timeout=3600", // 1 hour timeout
        "-o", "default_permissions" // permission is not supported that, this disables the permission check from Fuse side
    };


    public F1r3flyFS(Config config) {
        super(config);

        this.config = config;
        log = LoggerFactory.getLogger("F1r3flyFS (%s:%s)".formatted(config.clientHost, config.clientPort));
    }

    @Override
    public int create(String path, @mode_t long mode, FuseFileInfo fi) {
        log.debug("Called Create file {}", path);
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
            log.error("Error creating file {}", path, e);
            return -ErrorCodes.EIO();
        }
    }


    @Override
    public int getattr(String path, FileStat stat) {
        log.debug("Called Getattr {}", path);
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
            log.error("Error getting attributes for {}", path, e);
            return -ErrorCodes.EIO();
        }
    }

    private String getLastComponent(String path) {
        while (path.substring(path.length() - 1).equals(PathUtils.getPathDelimiterBasedOnOS())) {
            path = path.substring(0, path.length() - 1);
        }
        if (path.isEmpty()) {
            return "";
        }
        return path.substring(path.lastIndexOf(PathUtils.getPathDelimiterBasedOnOS()) + 1);
    }

    private MemoryPath getParentPath(String path) {
        return rootDirectory.find(path.substring(0, path.lastIndexOf(PathUtils.getPathDelimiterBasedOnOS())));
    }

    private MemoryPath getPath(String path) {
        return rootDirectory.find(path);
    }


    @Override
    public int mkdir(String path, @mode_t long mode) {
        try {
            log.debug("Called Mkdir {}", path);
            if (getPath(path) != null) {
                return -ErrorCodes.EEXIST();
            }
            MemoryPath parent = getParentPath(path);
            if (parent instanceof MemoryDirectory) {
                ((MemoryDirectory) parent).mkdir(getLastComponent(path), true);
                return SuccessCodes.OK;
            }
            log.warn("Parent of {} is not a directory: {}", path, parent == null ? "null" : path);
            return -ErrorCodes.ENOENT();
        } catch (Throwable e) {
            log.error("Error creating directory {}", path, e);
            return -ErrorCodes.EIO();
        }
    }


    @Override
    public int read(String path, Pointer buf, @size_t long size, @off_t long offset, FuseFileInfo fi) {
        log.trace("Called Read file {} with buffer size {} and offset {}", path, size, offset);
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
            log.warn("Error reading file {}", path, e);
            return -ErrorCodes.EIO();
        }
    }

    @Override
    public int readdir(String path, Pointer buf, FuseFillDir filter, @off_t long offset, FuseFileInfo fi) {
        log.debug("Called Readdir {}", path);
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
            log.error("Error reading directory {}", path, e);
            return -ErrorCodes.EIO();
        }
    }


    @Override
    public int statfs(String path, Statvfs stbuf) {
        // many calls, so trace lvl
        log.trace("Called Statfs {}", path);
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
            log.error("Error statfs", e);
            return -ErrorCodes.EIO();
        }
    }

    @Override
    public int rename(String path, String newName) {
        log.debug("Called Rename {} to {}", path, newName);
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
            p.rename(newName.substring(newName.lastIndexOf(PathUtils.getPathDelimiterBasedOnOS())), (MemoryDirectory) newParent, true, true);
//            ((MemoryDirectory) newParent).add(p, true);

            if (p instanceof MemoryFile)
                ((MemoryFile) p).onChange();

            return SuccessCodes.OK;
        } catch (Throwable e) {
            log.error("Error renaming file {}", path, e);
            return -ErrorCodes.EIO();
        }
    }

    @Override
    public int rmdir(String path) {
        log.debug("Called Rmdir {}", path);
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
            p.delete(true);
            return SuccessCodes.OK;
        } catch (Throwable e) {
            log.error("Error removing directory {}", path, e);
            return -ErrorCodes.EIO();
        }
    }

    @Override
    public int truncate(String path, long offset) {
        log.debug("Called Truncate file {}", path);
        try {
            MemoryPath p = getPath(path);
            if (p == null) {
                return -ErrorCodes.ENOENT();
            }
            if (!(p instanceof MemoryFile)) {
                return -ErrorCodes.EISDIR();
            }
            ((MemoryFile) p).truncate(offset, true);

            return SuccessCodes.OK;
        } catch (Throwable e) {
            log.error("Error truncating file {}", path, e);
            return -ErrorCodes.EIO();
        }
    }

    @Override
    public int unlink(String path) {
        log.debug("Called Unlink {}", path);
        try {
            MemoryPath p = getPath(path);
            if (p == null) {
                return -ErrorCodes.ENOENT();
            }
            p.delete(true);
            return SuccessCodes.OK;
        } catch (Throwable e) {
            log.error("Error unlinking file {}", path, e);
            return -ErrorCodes.EIO();
        }
    }

    @Override
    public int open(String path, FuseFileInfo fi) {
        log.debug("Called Open file {}", path);
        try {
            MemoryPath p = getPath(path);
            if (p == null) {
                return -ErrorCodes.ENOENT();
            }
            if (p instanceof MemoryFile) {
                ((MemoryFile) p).open();
                log.debug("Opened file {}", path);
            }
            return SuccessCodes.OK;
        } catch (Throwable e) {
            log.warn("Error opening file", e);
            return -ErrorCodes.EIO();
        }
    }

    @Override
    public int write(String path, Pointer buf, @size_t long size, @off_t long offset, FuseFileInfo fi) {
        log.trace("Called Write file {} with buffer size {} and offset {}", path, size, offset);
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
            log.warn("Error writing to file", e);
            return -ErrorCodes.EIO();
        }
    }

    @Override
    public int flush(String path, FuseFileInfo fi) {
        log.debug("Called Flush file {}", path);
        try {
            MemoryPath p = getPath(path);
            if (p == null) {
                return -ErrorCodes.ENOENT();
            }
            if (p instanceof MemoryFile) {
                ((MemoryFile) p).close();
                ((MemoryFile) p).onChange();
            }
//             TODO: needed?
//            ((MemoryDirectory) getParentPath(path)).triggerNotification("flush " + path);
            return SuccessCodes.OK;
        } catch (Throwable e) {
            log.warn("Error closing file", e);
            return -ErrorCodes.EIO();
        }
    }


    public void remount(Path mountPoint) throws PathIsNotADirectory, NoDataByPath, IOException, InterruptedException {
        remount(mountPoint, false, false, new String[]{});
    }

    public void remount(Path mountPoint, boolean blocking, boolean debug, String[] fuseOpts) throws PathIsNotADirectory, NoDataByPath, IOException, InterruptedException {
        log.debug("Called remount F1r3flyFS with mount name {}, mount point {}, blocking {}, debug, {}, fuse opts {}",
            config.mountName, mountPoint, blocking, debug, Arrays.toString(fuseOpts));
        try {

            MemoryPath root = fetchDirectoryFromShard(config.mountName, "", null);

            if (root instanceof MemoryDirectory) {
                this.rootDirectory = (MemoryDirectory) root;
            } else {
                throw new PathIsNotADirectory("Root path " + root.getAbsolutePath() + " is not a directory");
            }

            config.deployDispatcher.startBackgroundDeploy();
            NotificationsSubscriber.subscribe(config);

            startGRPCServer();

            super.mount(mountPoint, blocking, debug, fuseOpts);

        } catch (Throwable e) {
            log.error("Error re-mounting F1r3flyFS", e);

            if (this.rootDirectory != null) {
                this.rootDirectory = null;
            }

            // destroy background tasks and queue
            config.deployDispatcher.hardStop();

            throw e;
        }
    }

    private MemoryPath fetchDirectoryFromShard(String channelName, String name, MemoryDirectory parent) throws NoDataByPath {
        try {
            List<RhoTypes.Par> pars = config.f1r3flyAPI.findDataByName(channelName);

            RholangExpressionConstructor.ChannelData fileOrDir = RholangExpressionConstructor.parseChannelData(pars);

            if (fileOrDir.isDir()) {
                MemoryDirectory dir =
                    new MemoryDirectory(config, name, parent, false);

                fileOrDir.children().forEach(childName -> {
                    try {
                        MemoryPath child = fetchDirectoryFromShard(channelName + PathUtils.getPathDelimiterBasedOnOS() + childName, childName, dir);
                        dir.addChildren(child, false);
                    } catch (NoDataByPath e) {
                        log.error("Error fetching child {} of {} from shard", childName, channelName, e);
                        // skip for now
                    }
                });

                return dir;

            } else {
                MemoryFile file = new MemoryFile(config, PathUtils.getFileName(channelName), parent, false);

                file.fetchContent(fileOrDir.firstChunk(), fileOrDir.otherChunks());

                return file;
            }

        } catch (NoDataByPath e) {
            throw e;
        } catch (Throwable e) {
            log.error("Error fetching path {} from shard", channelName, e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public void mount(Path mountPoint, boolean blocking, boolean debug, String[] fuseOpts) {
        log.debug("Called Mounting F1r3flyFS on {} with opts {}", mountPoint, Arrays.toString(fuseOpts));

        try {

            String mountName = "f1r3flyfs" + ThreadLocalRandom.current().nextInt();

            config = new Config(
                config.clientHost,
                config.clientPort,
                mountName,
                mountPoint,
                config.deployDispatcher,
                config.f1r3flyAPI
            );

            config.deployDispatcher.startBackgroundDeploy();

            // combine fuseOpts and MOUNT_OPTIONS
            String[] allFuseOpts = Arrays.copyOf(fuseOpts, fuseOpts.length + MOUNT_OPTIONS.length);
            System.arraycopy(MOUNT_OPTIONS, 0, allFuseOpts, fuseOpts.length, MOUNT_OPTIONS.length);

            this.rootDirectory = new MemoryDirectory(config, "", true);

            NotificationsSubscriber.subscribe(config);

            startGRPCServer();

            super.mount(mountPoint, blocking, debug, fuseOpts);

        } catch (Throwable e) {
            log.error("Error mounting F1r3flyFS", e);

            if (this.rootDirectory != null) {
                this.rootDirectory = null;
            }

            // destroy background tasks and queue
            config.deployDispatcher.hardStop();

            throw new FuseException("Error mounting F1r3flyFS", e);
        }
    }

    private void startGRPCServer() throws IOException, InterruptedException {
        UpdateNotificationHandler updateNotificationHandler = new UpdateNotificationHandler() {
            private String prependMountName(String path) {
                return config.mountName + path;
            }


            @Override
            public ExternalCommunicationServiceV1.UpdateNotificationResponse handle(ExternalCommunicationServiceCommon.UpdateNotification notification) {
                if (rootDirectory != null) {
                    log.info("sync: Received notification to {}:{} about {}", notification.getClientHost(), notification.getClientPort(), notification.getPayload());
                    // TODO: make async and don't block gRPC server

                    NotificationConstructor.NotificationPayload reason =
                        NotificationConstructor.NotificationPayload.parseNotification(notification.getPayload());

                    @Nullable
                    MemoryPath p = rootDirectory.find(reason.path());


                    MemoryPath parent =
                        rootDirectory.getName().equals(reason.path()) ? // it's a root, so no parent
                            null :
                            getParentPath(reason.path());

                    if (parent != null && !(parent instanceof MemoryDirectory)) {
                        log.warn("sync: Parent of {} is not a directory", reason.path());
                        return ExternalCommunicationServiceV1.UpdateNotificationResponse.newBuilder().build();
                    }

                    switch (reason.reason()) {
                        case NotificationConstructor.NotificationReasons.FILE_CREATED:
                        case NotificationConstructor.NotificationReasons.DIRECTORY_CREATED:
                            if (parent == null) {
                                log.warn("sync: Directory created but the parent is null. Perhabs got notification about creation of root directory {}. Skipping", reason.path());
                                return ExternalCommunicationServiceV1.UpdateNotificationResponse.newBuilder().build();
                            }
                            if (p != null) { // force delete from local cache
                                log.warn("sync: {} already exists", reason.path());

                                p.delete(false);
                            }


                            try {
                                String channelName = prependMountName(reason.path());
                                MemoryPath dir = fetchDirectoryFromShard(channelName, PathUtils.getFileName(reason.path()), (MemoryDirectory) parent);
                                ((MemoryDirectory) parent).addChildren(dir, false);
                                log.info("sync: Created {}", reason.path());
                            } catch (NoDataByPath e) {
                                log.error("sync: Error fetching file {} from shard", reason.path(), e);
                            }


                            break;
                        case NotificationConstructor.NotificationReasons.TRUNCATED:
                            if (p instanceof MemoryFile) {
                                try {
                                    ((MemoryFile) p).truncate(0, false);
                                    log.info("sync: Truncated file {}", reason.path());
                                } catch (IOException e) {
                                    log.error("sync: Error truncating file {}", reason.path(), e);
                                }
                            }
                            break;

                        case NotificationConstructor.NotificationReasons.RENAMED:
                            if (p != null) {
                                try {
                                    if (reason.newPath() != null) {
                                        MemoryPath newParent = getParentPath(reason.newPath());
                                        if (!(newParent instanceof MemoryDirectory)) {
                                            log.warn("sync: New parent of {} is not a directory", reason.newPath());
                                            return ExternalCommunicationServiceV1.UpdateNotificationResponse.newBuilder().build();
                                        }
                                        String newName = PathUtils.getFileName(reason.newPath());
                                        p.rename(newName, (MemoryDirectory) newParent, false, false);
                                        log.info("sync: Renamed {} to {}", reason.path(), reason.newPath());
                                    } else {
                                        log.warn("sync: New path is null for renaming {}", reason.path());
                                    }
                                } catch (IOException e) {
                                    log.error("sync: Error renaming file {}", reason.path(), e);
                                }
                            } else {
                                log.warn("sync: {} does not exist", reason.path());
                            }
                            break;

                        case NotificationConstructor.NotificationReasons.FILE_WROTE:
                            if (p != null && !(p instanceof MemoryFile)) {
                                log.info("sync: {} is not a file. Path from notification {}", p.getAbsolutePath(), reason.path());
                                return ExternalCommunicationServiceV1.UpdateNotificationResponse.newBuilder().build();
                            }
                            try {
                                if (p != null) {
                                    p.delete(false);
                                }
                                String channelName = prependMountName(reason.path());
                                MemoryPath file = fetchDirectoryFromShard(channelName, PathUtils.getFileName(reason.path()), (MemoryDirectory) parent);
                                ((MemoryDirectory) parent).addChildren(file, false);
                                log.info("sync: Updated content for file {} (parent: {}, childs: {})", reason.path(), parent.getName(), Arrays.toString(((MemoryDirectory) parent).getChild().stream().map((f) -> f.getName()).toArray()));
                            } catch (NoDataByPath e) {
                                log.error("sync: Error fetching content for file {}", reason.path(), e);
                            }
                            break;

                        case NotificationConstructor.NotificationReasons.DELETED:
                            if (p != null) {
                                p.delete(false);
                                log.info("sync: Deleted {}", reason.path());
                            }
                            break;

                        default:
                            log.warn("sync: Unknown reason {}", reason.reason());
                    }
                }

                return ExternalCommunicationServiceV1.UpdateNotificationResponse.newBuilder().build();
            }
        };

        this.grpcServer = F1r3flyDriveServer.create(config.clientPort, updateNotificationHandler);
        this.grpcServer.start();
    }

    public void waitOnBackgroundThread() {
        log.info("Called waitOnBackgroundThread");
        try {
            config.deployDispatcher.waitOnEmptyQueue();
            log.info("waitOnBackgroundThread completed");
        } catch (Throwable e) {
            log.error("Error destroying F1r3flyFS", e);
            throw e;
        }
    }

    @Override
    public void umount() {
        log.info("Called Umounting F1r3flyFS");
        try {
            if (!mounted.get()) {
                return;
            }

            NotificationsSubscriber.unsubscribe(config);
            this.grpcServer.shutdownGracefully();

            waitOnBackgroundThread();

            config.deployDispatcher.hardStop();

            this.rootDirectory = null;
            super.umount();
        } catch (Throwable e) {
            log.error("Error unmounting F1r3flyFS", e);
            throw e;
        }
    }

    // public because of this method is used in tests
    public String prependMountName(String path) {
        // example: f1r3flyfs-123123123/path-to-file
        return config.mountName + path;
    }

    public String getMountName() {
        return config.mountName;
    }

    protected boolean isMounted() {
        return this.rootDirectory != null;
    }
}
