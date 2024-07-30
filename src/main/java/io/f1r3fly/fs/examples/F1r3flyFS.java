package io.f1r3fly.fs.examples;

import io.f1r3fly.fs.*;
import io.f1r3fly.fs.examples.datatransformer.AESCipher;
import io.f1r3fly.fs.examples.storage.DiskCache;
import io.f1r3fly.fs.examples.storage.F1f3flyFSStorage;
import io.f1r3fly.fs.examples.storage.errors.*;
import io.f1r3fly.fs.examples.storage.grcp.F1r3flyApi;
import io.f1r3fly.fs.examples.storage.rholang.RholangExpressionConstructor;
import io.f1r3fly.fs.struct.FileStat;
import io.f1r3fly.fs.struct.FuseFileInfo;
import io.f1r3fly.fs.utils.PathUtils;
import jnr.ffi.Pointer;
import jnr.ffi.types.off_t;
import jnr.ffi.types.size_t;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

public class F1r3flyFS extends FuseStubFS {

    private static final Logger LOGGER = LoggerFactory.getLogger(F1r3flyFS.class);

    private final F1r3flyApi f1R3FlyApi;
    private final AESCipher aesCipher;
    private final DiskCache cache;
    private F1f3flyFSStorage storage;


    // it should be a number that can be divisible by
    // * 16 because of AES block size
    private final int MAX_CHUNK_SIZE = 160 * 1024 * 1024; // 160 MB

    private final String[] MOUNT_OPTIONS = {
        // refers to https://github.com/osxfuse/osxfuse/wiki/Mount-options#iosize
        "-o", "noappledouble",
        "-o", "daemon_timeout=3600" // 1 hour timeout (fir socket is unavailable)
    };


    public F1r3flyFS(F1r3flyApi f1R3FlyApi, AESCipher aesCipher) {
        super(); // no need to call Fuse constructor

        this.f1R3FlyApi = f1R3FlyApi;
        this.aesCipher = aesCipher;
        this.cache = new DiskCache();
    }

    private void deployChunkFromCacheToNode(String path, long offset) throws F1r3flyDeployError, NoDataByPath, CacheIOException, PathIsNotAFile {
        Boolean wasModified = this.cache.wasModified(path);

        if (wasModified == null || !wasModified) {
            LOGGER.debug("No need to deploy a chunk from cache to node: {} with offset {}", path, offset);
            this.cache.remove(path); // just drop the cache; data was not changed
            return;
        }

        long cachedSize = this.cache.getSize(path);

        if (offset < cachedSize) {

            long remaining = cachedSize - offset;
            long chunkSize = Math.min(MAX_CHUNK_SIZE, remaining);

            LOGGER.debug("Deploying a chunk from cache to node: {} (size {}) with offset {}", path, chunkSize, offset);

            byte[] chunkToWrite = this.cache.read(path, offset, chunkSize);
            byte[] encryptedChunk = PathUtils.isEncryptedExtension(path) ? aesCipher.encrypt(chunkToWrite) : chunkToWrite;
            this.storage.appendFile(path, encryptedChunk, chunkToWrite.length);

            deployChunkFromCacheToNode(path, offset + chunkSize); // continue
        } else {
            // end

            if (PathUtils.isDeployableFile(path)) {
                deployingRholang(path);
            }

            LOGGER.debug("Ended deploying a chunk from cache to node: {} with offset {}", path, offset);

            this.cache.remove(path);
        }

    }

    private void deployingRholang(String path) {
        LOGGER.debug("Deploying a file: {}", path);

        try {
            String newFile = this.storage.deployFile(path); //dont read it form node; its present at a cache
            this.storage.addToParent(newFile);
        } catch (NoDataByPath | PathIsNotAFile | PathIsNotADirectory | RuntimeException | DirectoryNotFound | F1r3flyDeployError e) {
            LOGGER.error("Internal error: can't deploy {}", path, e);
            throw new RuntimeException(e);
        } catch (Throwable e) {
            LOGGER.error("Unexpected error: failed to deploy {}", path, e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public int getattr(String path, FileStat stat) {
        LOGGER.debug("Called getting attributes for path: {}", prependMountName(path));
        try {
            checkMount();
            checkPath(path);

            long cachedSize = this.cache.getSize(prependMountName(path));

            if (cachedSize > 0) { // cached file, no need to fetch data from the node
                stat.st_mode.set(FileStat.S_IFREG | 0777);
                stat.st_uid.set(getContext().uid.get());
                stat.st_gid.set(getContext().gid.get());
                stat.st_size.set(cachedSize);

                return SuccessCodes.OK;
            }

            RholangExpressionConstructor.ChannelData dirOrFile =
                this.storage.getTypeAndSize(prependMountName(path));

            LOGGER.info("Path {} is '{}' type and size is {}",
                prependMountName(path), dirOrFile.type(), dirOrFile.size());

            if (dirOrFile.isFile()) {

                long size = dirOrFile.size();

                stat.st_mode.set(FileStat.S_IFREG | 0777);
                stat.st_uid.set(getContext().uid.get());
                stat.st_gid.set(getContext().gid.get());
                stat.st_size.set(size);

                return SuccessCodes.OK;
            } else if (dirOrFile.isDir()) {
                stat.st_mode.set(FileStat.S_IFDIR | 0444);
                stat.st_uid.set(getContext().uid.get());
                stat.st_gid.set(getContext().gid.get());

                return SuccessCodes.OK;
            } else {
                LOGGER.info("Not existing path {}", prependMountName(path));
                return -ErrorCodes.ENOENT(); // not found
            }
        } catch (NoDataByPath e) { // no data at path
            LOGGER.debug("No file or directory by the path", e);
            return -ErrorCodes.ENOENT(); // not found
        } catch (FuseException e) {
            LOGGER.warn("Failed to get attributes", e);
            return -ErrorCodes.EBUSY();
        } catch (Throwable e) {
            LOGGER.error("Failed to get attributes", e);
            return -ErrorCodes.EIO(); // general error
        }
    }

    @Override
    public int open(String path, FuseFileInfo fi) {
        LOGGER.debug("Called open: {}", prependMountName(path));

        try {
            checkMount();
            checkPath(path);

            if (PathUtils.isEncryptedExtension(path)) {
                byte[] encrypted = this.storage.readFile(prependMountName(path));
                byte[] decrypted = aesCipher.decrypt(encrypted);
                this.cache.write(prependMountName(path), decrypted, 0, false);
            } else {
                byte[] data = this.storage.readFile(prependMountName(path));
                this.cache.write(prependMountName(path), data, 0, false);
            }

            return SuccessCodes.OK;
        } catch (NoDataByPath e) {
            LOGGER.warn("Failed to read file", e);
            return -ErrorCodes.ENOENT(); // not found
        } catch (PathIsNotAFile e) {
            LOGGER.warn("Path is not a file", e);
            return -ErrorCodes.EISDIR(); // is a directory?
        } catch (CacheIOException e) {
            LOGGER.error("Failed to read a file", e);
            return -ErrorCodes.EIO(); // general error
        } catch (Throwable e) {
            LOGGER.error("Failed to open file", e);
            return -ErrorCodes.EIO(); // general error
        }
    }

    @Override
    public synchronized int flush(String path, FuseFileInfo fi) {
        LOGGER.debug("Called flush: {}", prependMountName(path));
        try {
            deployChunkFromCacheToNode(prependMountName(path), 0);
            return SuccessCodes.OK;
        } catch (NoDataByPath e) {
            LOGGER.warn("Failed to read file", e);
            return -ErrorCodes.ENOENT(); // not found
        } catch (PathIsNotAFile e) {
            LOGGER.warn("Path is not a file", e);
            return -ErrorCodes.EISDIR(); // is a directory?
        } catch (F1r3flyDeployError e) {
            LOGGER.error("Failed to deploy", e);
            return -ErrorCodes.EIO(); // general error
        } catch (CacheIOException e) {
            LOGGER.error("Cache error", e);
            return -ErrorCodes.EIO(); // general error
        } catch (Throwable e) {
            LOGGER.error("Failed to flush", e);
            return -ErrorCodes.EIO(); // general error
        }
    }

    @Override
    public synchronized int read(String path, Pointer buf, @size_t long size, @off_t long offset, FuseFileInfo fi) {
        LOGGER.debug("Called read: {}, size {}, offset {}", prependMountName(path), size, offset);
        try {
            checkMount();
            checkPath(path);

            byte[] fileData = this.cache.read(prependMountName(path), offset, size);
            buf.put(0, fileData, 0, fileData.length);

            return fileData.length;
        } catch (NoDataByPath e) {
            LOGGER.warn("Failed to read file", e);
            return -ErrorCodes.ENOENT(); // not found
        } catch (PathIsNotAFile e) {
            LOGGER.warn("Path is not a file", e);
            return -ErrorCodes.EISDIR(); // is a directory?
        } catch (F1r3flyDeployError e) {
            LOGGER.error("Failed to deploy", e);
            return -ErrorCodes.EIO(); // general error
        } catch (CacheIOException e) {
            LOGGER.error("Failed to read a file", e);
            return -ErrorCodes.EIO(); // general error
        } catch (Throwable e) {
            LOGGER.error("Failed to read file", e);
            return -ErrorCodes.EIO(); // general error
        }
    }



    @Override
    public synchronized int write(String path, Pointer buf, @size_t long size, @off_t long offset, FuseFileInfo fi) {
        LOGGER.debug("Called write file: {} with parameters size {} offset {}", prependMountName(path), size, offset);
        try {
            checkMount();
            checkPath(path);

            byte[] read = new byte[(int) size];
            buf.get(0, read, 0, (int) size);

            this.cache.write(prependMountName(path), read, offset, true);

            return (int) size; // return number of bytes written
        } catch (CacheIOException e) {
            LOGGER.error("Failed to cache a file", e);
            return -ErrorCodes.EIO(); // general error
        } catch (Throwable e) {
            LOGGER.error("Failed to write file", e);
            return -ErrorCodes.EIO(); // general error
        }
    }

    @Override
    public int create(String path, long mode, FuseFileInfo fi) {
        LOGGER.debug("Called create file: {}", prependMountName(path));
        try {
            checkMount();
            checkPath(path);

            this.storage.createFile(prependMountName(path), new byte[0], 0);
            this.storage.addToParent(prependMountName(path));

            this.cache.write(prependMountName(path), new byte[0], 0, true);

            return SuccessCodes.OK;

        } catch (DirectoryNotFound e) {
            LOGGER.warn("Directory not found", e);
            return -ErrorCodes.ENOENT(); // not found
        } catch (F1r3flyDeployError e) {
            LOGGER.error("Failed to deploy", e);
            return -ErrorCodes.EIO(); // general error
        } catch (PathIsNotADirectory e) {
            LOGGER.warn("Path is not a directory", e);
            return -ErrorCodes.EIO(); // is a directory?
        } catch (CacheIOException e) {
            LOGGER.error("Failed to cache a file", e);
            return -ErrorCodes.EIO(); // general error
        } catch (Throwable e) {
            LOGGER.error("Failed to create file", e);
            return -ErrorCodes.EIO(); // general error
        }
    }

    @Override
    public int truncate(String path, long size) {
        LOGGER.debug("Called truncate file {} and size {}", prependMountName(path), size);
        try {
            checkMount();
            checkPath(path);

            if (size == 0) { // support the truncate to zero only for now
                long actualSize = this.storage.getTypeAndSize(prependMountName(path)).size();

                if (actualSize == 0) {
                    return SuccessCodes.OK;
                }

                //TODO: truncate file using a size
                this.cache.remove(prependMountName(path));
                this.storage.deleteFile(prependMountName(path));
                this.storage.createFile(prependMountName(path), new byte[0], 0);

                return SuccessCodes.OK;
            } else {
                return -ErrorCodes.EINVAL(); // invalid argument
            }

        } catch (F1r3flyDeployError e) {
            LOGGER.error("Failed to deploy", e);
            return -ErrorCodes.EIO(); // general error
        } catch (NoDataByPath e) {
            LOGGER.info("No data by path", e);
            return -ErrorCodes.ENOENT(); // not found
        } catch (PathIsNotAFile e) {
            LOGGER.info("Path is not a file", e);
            return -ErrorCodes.EISDIR(); // is a directory?
        } catch (Throwable e) {
            LOGGER.error("Failed to truncate", e);
            return -ErrorCodes.EIO(); // general error
        }
    }

    @Override
    public int mkdir(String path, long mode) {
        LOGGER.debug("Called mkdir: {}", prependMountName(path));
        try {
            checkMount();
            checkPath(path);

            this.storage.addToParent(prependMountName(path)); // fails if parent not found
            this.storage.createDir(prependMountName(path));

            return SuccessCodes.OK;

        } catch (DirectoryNotFound e) {
            LOGGER.warn("Directory not found", e);
            return -ErrorCodes.ENOENT(); // not found
        } catch (F1r3flyDeployError e) {
            LOGGER.error("Failed to deploy", e);
            return -ErrorCodes.EIO(); // general error
        } catch (PathIsNotADirectory e) {
            LOGGER.warn("Path is not a directory", e);
            return -ErrorCodes.EIO(); // is a directory?
        } catch (Throwable e) {
            LOGGER.error("Failed to mkdir", e);
            return -ErrorCodes.EIO(); // general error
        }
    }

    @Override
    public int readdir(String path, Pointer buf, FuseFillDir filter, long offset, FuseFileInfo fi) {
        LOGGER.debug("Called readdir: {}", prependMountName(path));
        try {
            Set<String> childs = this.storage.readDir(prependMountName(path));

            filter.apply(buf, ".", null, 0);
            filter.apply(buf, "..", null, 0);

            LOGGER.debug("Childs: {}", childs);

            for (String child : childs) {
                LOGGER.debug("adding child: {}", child);
                filter.apply(buf, child, null, 0);
            }

            return SuccessCodes.OK;
        } catch (DirectoryNotFound e) {
            LOGGER.warn("Directory not found", e);
            return -ErrorCodes.ENOENT(); // not found
        } catch (NoDataByPath e) {
            LOGGER.error("Failed to get data", e);
            return -ErrorCodes.EIO(); // general error
        } catch (PathIsNotADirectory e) {
            LOGGER.warn("Path is not a directory", e);
            return -ErrorCodes.EIO(); // is a directory?
        } catch (Throwable e) {
            LOGGER.error("Failed to read directory", e);
            return -ErrorCodes.EIO(); // general error
        }
    }

    @Override
    public int rename(String oldpath, String newpath) {
        LOGGER.debug("Called rename: {} -> {}", prependMountName(oldpath), prependMountName(newpath));
        try {
            checkMount();
            checkPath(oldpath);
            checkPath(newpath);

            this.storage.addToParent(prependMountName(newpath)); // fails if parent not found
            this.storage.removeFromParent(prependMountName(oldpath)); // fails if parent not found

            if (PathUtils.isEncryptedExtension(oldpath) && !PathUtils.isEncryptedExtension(newpath)) {
                // was encrypted, now not encrypted
                byte[] encrypted = this.storage.readFile(prependMountName(oldpath));
                byte[] decrypted = aesCipher.decrypt(encrypted);
                this.storage.createFile(prependMountName(newpath), decrypted, decrypted.length);
                this.storage.deleteFile(prependMountName(oldpath));
            } else if (PathUtils.isEncryptedExtension(newpath) && !PathUtils.isEncryptedExtension(oldpath)) {
                // was not encrypted, now encrypted
                byte[] notEncrypted = this.storage.readFile(prependMountName(oldpath));
                byte[] encrypted = aesCipher.encrypt(notEncrypted);
                this.storage.createFile(prependMountName(newpath), encrypted, notEncrypted.length);
                this.storage.deleteFile(prependMountName(oldpath));
            } else {
                // just rename
                this.storage.rename(prependMountName(oldpath), prependMountName(newpath));
            }

            if (PathUtils.isDeployableFile(newpath)) {
                deployingRholang(prependMountName(newpath));
            }

            return SuccessCodes.OK;

        } catch (DirectoryNotFound | NoDataByPath e) {
            LOGGER.warn("Directory not found", e);
            return -ErrorCodes.ENOENT(); // not found
        } catch (F1r3flyDeployError e) {
            LOGGER.error("Failed to deploy", e);
            return -ErrorCodes.EIO(); // general error
        } catch (PathIsNotADirectory e) {
            LOGGER.warn("Path is not a directory", e);
            return -ErrorCodes.EIO(); // is a directory?
        } catch (DirectoryIsNotEmpty e) {
            LOGGER.warn("Directory is not empty", e);
            return -ErrorCodes.ENOTEMPTY(); // directory is not empty
        } catch (AlreadyExists e) {
            LOGGER.warn("Already exists", e);
            return -ErrorCodes.EEXIST(); // already exists
        } catch (PathIsNotAFile e) {
            LOGGER.warn("Path is not a file", e);
            return -ErrorCodes.EISDIR(); // is a directory?
        } catch (Throwable e) {
            LOGGER.error("Failed to rename", e);
            return -ErrorCodes.EIO(); // general error
        }
    }

    @Override
    public int unlink(String path) {
        LOGGER.debug("Called unlink: {}", prependMountName(path));

        try {
            checkMount();
            checkPath(path);

            this.cache.remove(prependMountName(path));
            this.storage.deleteFile(prependMountName(path));
            this.storage.removeFromParent(prependMountName(path));

            return SuccessCodes.OK;

        } catch (DirectoryNotFound e) {
            LOGGER.warn("Directory not found", e);
            return -ErrorCodes.ENOENT(); // not found
        } catch (F1r3flyDeployError e) {
            LOGGER.error("Failed to deploy", e);
            return -ErrorCodes.EIO(); // general error
        } catch (PathIsNotADirectory e) {
            LOGGER.warn("Path is not a directory", e);
            return -ErrorCodes.EIO(); // is a directory?
        } catch (NoDataByPath e) {
            LOGGER.info("No data by path", e);
            return -ErrorCodes.ENOENT(); // not found
        } catch (PathIsNotAFile e) {
            LOGGER.info("Path is not a file", e);
            return -ErrorCodes.EISDIR(); // is a directory?
        }
    }

    @Override
    public int rmdir(String path) {
        LOGGER.debug("Called rmdir: {}", prependMountName(path));

        try {
            checkMount();
            checkPath(path);

            this.storage.deleteDir(prependMountName(path));
            this.storage.removeFromParent(prependMountName(path));

            return SuccessCodes.OK;

        } catch (DirectoryNotFound e) {
            LOGGER.warn("Directory not found", e);
            return -ErrorCodes.ENOENT(); // not found
        } catch (F1r3flyDeployError e) {
            LOGGER.error("Failed to deploy", e);
            return -ErrorCodes.EIO(); // general error
        } catch (PathIsNotADirectory e) {
            LOGGER.warn("Path is not a directory", e);
            return -ErrorCodes.EIO(); // is a directory?
        } catch (DirectoryIsNotEmpty e) {
            LOGGER.warn("Directory is not empty", e);
            return -ErrorCodes.ENOTEMPTY(); // directory is not empty
        } catch (Throwable e) {
            LOGGER.error("Failed to rmdir", e);
            return -ErrorCodes.EIO(); // general error
        }
    }

    @Override
    public void mount(Path mountPoint, boolean blocking, boolean debug, String[] fuseOpts) {
        LOGGER.debug("Called mounting filesystem with mountPoint: {}", mountPoint.toString());

        if (this.mounted.get()) {
            throw new FuseException("Already mounted");
        }

        synchronized (this) {

            generateMountName();
            this.storage = new F1f3flyFSStorage(this.f1R3FlyApi);

            // root path
            try {
                this.storage.createDir(this.mountName + ":" + PathUtils.getPathDelimiterBasedOnOS() + mountPoint.toAbsolutePath());

            } catch (F1r3flyDeployError e) {
                LOGGER.error("Failed to deploy", e);
                throw new FuseException("Failed to deploy", e);
            }

        }

        try {
            String[] mergedFuseOpts = Stream.concat(Arrays.stream(MOUNT_OPTIONS), Arrays.stream(fuseOpts))
                .toArray(String[]::new);

            // call super method to mount
            super.mount(mountPoint, blocking, debug, mergedFuseOpts);
        } catch (FuseException e) {
            forgetMountIds();
            throw new FuseException("Failed to mount", e);
        }
    }

    @Override
    public void umount() {
        LOGGER.debug("Called unmounting filesystem");

        super.umount();

        forgetMountIds();
    }

    private void forgetMountIds() {
        synchronized (this) {

            if (this.mountPoint == null) {
                LOGGER.info("Already unmounted");
            }

            this.storage = null;
            this.mountName = null;
        }
    }

    private void checkPath(String path) {
        if (path == null || !path.startsWith(PathUtils.getPathDelimiterBasedOnOS())) {
            throw new IllegalArgumentException("Invalid path: %s".formatted(path));
        }
    }

    private void checkMount() throws FuseException {
        if (this.mountPoint == null) {
            throw new FuseException("Not mounted");
        }
    }

    // public because of this method is used in tests
    public String prependMountName(String path) {
        // example: f1r3flyfs-123123123://mounted-path/path-to-file
        String fullPath = this.mountName + ":" + PathUtils.getPathDelimiterBasedOnOS() + this.mountPoint.toAbsolutePath() + path;

        if (fullPath.endsWith(PathUtils.getPathDelimiterBasedOnOS())) {
            fullPath = fullPath.substring(0, fullPath.length() - 1);
        }

        return fullPath;
    }

    protected void generateMountName() {
        this.mountName = "f1r3flyfs" + ThreadLocalRandom.current().nextInt();
    }
}
