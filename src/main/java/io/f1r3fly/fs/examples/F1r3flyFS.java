package io.f1r3fly.fs.examples;

import io.f1r3fly.fs.*;
import io.f1r3fly.fs.examples.storage.F1f3flyFSStorage;
import io.f1r3fly.fs.examples.storage.F1r3flyFixedFSStorage;
import io.f1r3fly.fs.examples.storage.FSStorage;
import io.f1r3fly.fs.examples.storage.errors.*;
import io.f1r3fly.fs.examples.storage.grcp.F1r3flyApi;
import io.f1r3fly.fs.struct.FileStat;
import io.f1r3fly.fs.struct.FuseFileInfo;
import io.f1r3fly.fs.utils.PathUtils;
import jnr.ffi.Pointer;
import jnr.ffi.types.off_t;
import jnr.ffi.types.size_t;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

public class F1r3flyFS extends FuseStubFS {

    private static final Logger LOGGER = LoggerFactory.getLogger(F1r3flyFS.class);

    private final F1r3flyApi f1R3FlyApi;
    private FSStorage storage;
    private String lastBlockHash;

    // it should be a number that can be divisible by 3
    // because we want to avoid a padding at the end of a base64 string
    private final int MAX_CHUNK_SIZE = 6 * 1024 * 1024; // 6MB

    private final String[] MOUNT_OPTIONS = {
        // refers to https://github.com/osxfuse/osxfuse/wiki/Mount-options#iosize
        "-o", "noappledouble"
    };

    private final ConcurrentHashMap<String, BlockingQueue<Byte>> cache;

    private void cacheAndAppendChunk(String path, byte[] data) throws F1r3flyDeployError, IOException {

        BlockingQueue<Byte> cached = cache.get(path);

        if (cached == null) {
            cached = new LinkedBlockingQueue<>();
        }

        for (byte b : data) {
            cached.add(b);
        }

        this.cache.put(path, cached);

        if (cached.size() >= MAX_CHUNK_SIZE) {
            appendChunkNow(path);
        }

    }

    private void appendAllChunks(String path) throws F1r3flyDeployError {
        if (cache.containsKey(path)) {
            appendChunkNow(path);

            appendAllChunks(path);
        }

    }

    private void appendChunkNow(String path) throws F1r3flyDeployError {
        if (cache.containsKey(path)) {
            BlockingQueue<Byte> cached = cache.get(path);

            int chunkSize = Math.min(MAX_CHUNK_SIZE, cached.size());
            ArrayList<Byte> buffer = new ArrayList<>();

            cached.drainTo(buffer, chunkSize);
            // convert to byte array
            byte[] chunkToWrite = new byte[buffer.size()];
            for (int i = 0; i < buffer.size(); i++) {
                chunkToWrite[i] = buffer.get(i);
            }

            String encodedChunk = Base64.getEncoder().encodeToString(chunkToWrite);
            this.lastBlockHash = this.storage.appendFile(path, encodedChunk, this.lastBlockHash).blockHash();

            if (cached.isEmpty()) {
                LOGGER.debug("Cache is empty, removing path: {}", path);
                cache.remove(path);
            }
        }
    }


    public F1r3flyFS(F1r3flyApi f1R3FlyApi) {
        super(); // no need to call Fuse constructor

        this.f1R3FlyApi = f1R3FlyApi;
        this.cache = new ConcurrentHashMap<>();
    }

    @Override
    public int release(String path, FuseFileInfo fi) {
        LOGGER.debug("Called release: {}", prependMountName(path));
        try {
            checkMount();
            checkPath(path);

            appendAllChunks(prependMountName(path));

            return SuccessCodes.OK;
        } catch (F1r3flyDeployError e) {
            LOGGER.error("Failed to deploy", e);
            return -ErrorCodes.EIO(); // general error
        }
    }

    @Override
    public int getattr(String path, FileStat stat) {
        LOGGER.debug("Called getting attributes for path: {}", prependMountName(path));
        try {
            checkMount();
            checkPath(path);

            FSStorage.TypeAndSize typeAndSize = this.storage.getTypeAndSize(prependMountName(path), this.lastBlockHash).payload();

            LOGGER.info("Path {} is '{}' type and size is {}",
                prependMountName(path), typeAndSize.type(), typeAndSize.size());

            if (typeAndSize.type().equals(F1f3flyFSStorage.FILE_TYPE)) {

                Queue<Byte> cached = cache.get(prependMountName(path));
                int size = (int) (cached == null ? typeAndSize.size() : typeAndSize.size() + cached.size());

                stat.st_mode.set(FileStat.S_IFREG | 0777);
                stat.st_uid.set(getContext().uid.get());
                stat.st_gid.set(getContext().gid.get());
                stat.st_size.set(size);

                return SuccessCodes.OK;
            } else if (typeAndSize.type().equals(F1f3flyFSStorage.DIR_TYPE)) {
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
    public int read(String path, Pointer buf, @size_t long size, @off_t long offset, FuseFileInfo fi) {
        LOGGER.debug("Called read: {}, size {}, offset {}", prependMountName(path), size, offset);
        try {
            checkMount();
            checkPath(path);

            appendAllChunks(prependMountName(path));

            String fileContent = this.storage.readFile(prependMountName(path), this.lastBlockHash).payload();

            byte[] decoded = Base64.getDecoder().decode(fileContent);
            // slice the buffer to the size
            byte[] sliced = Arrays.copyOfRange(decoded, (int) offset, (int) (offset + size)); //TODO don't fetch all data
            buf.put(0, sliced, 0, sliced.length);

            return sliced.length;
        } catch (NoDataByPath e) {
            LOGGER.warn("Failed to read file", e);
            return -ErrorCodes.ENOENT(); // not found
        } catch (PathIsNotAFile e) {
            LOGGER.warn("Path is not a file", e);
            return -ErrorCodes.EISDIR(); // is a directory?
        } catch (F1r3flyDeployError e) {
            LOGGER.error("Failed to deploy", e);
            return -ErrorCodes.EIO(); // general error
        }
    }

    @Override
    public int write(String path, Pointer buf, @size_t long size, @off_t long offset, FuseFileInfo fi) {
//        LOGGER.debug("Called write file: {} with parameters size {} offset {}", prependMountName(path), size, offset);
        try {
            checkMount();
            checkPath(path);

            // it's too slow to check a parent on each chunk write
//            Set<String> siblings = this.storage.readDir(prependMountName(PathUtils.getParentPath(path)), this.lastBlockHash).payload(); // check if path is a directory
//            String filename = PathUtils.getFileName(path);
//            if (!siblings.contains(filename)) {
//                // no need to add to parent if already added
//                this.lastBlockHash = this.storage.addToParent(prependMountName(path), this.lastBlockHash).blockHash();
//            }

            byte[] read = new byte[(int) size];
            buf.get(0, read, 0, (int) size);

            cacheAndAppendChunk(prependMountName(path), read);

            return (int) size; // return number of bytes written
//        } catch (DirectoryNotFound | NoDataByPath e) {
//            LOGGER.warn("Directory not found", e);
//            return -ErrorCodes.ENOENT(); // not found
        } catch (F1r3flyDeployError e) {
            LOGGER.error("Failed to deploy", e);
            return -ErrorCodes.EIO(); // general error
//        } catch (PathIsNotADirectory e) {
//            LOGGER.warn("Path is not a directory", e);
//            return -ErrorCodes.EIO(); // is a directory?
        } catch (IOException e) {
            LOGGER.error("Failed to cache a file", e);
            return -ErrorCodes.EIO(); // general error
        }
    }

    @Override
    public int create(String path, long mode, FuseFileInfo fi) {
        LOGGER.debug("Called create file: {}", prependMountName(path));
        try {
            checkMount();
            checkPath(path);

            this.lastBlockHash = this.storage.createFile(prependMountName(path), "", this.lastBlockHash).blockHash();
            this.lastBlockHash = this.storage.addToParent(prependMountName(path), this.lastBlockHash).blockHash();

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
        }
    }

    @Override
    public int truncate(String path, long size) {
        LOGGER.debug("Called truncate file {} and size {}", prependMountName(path), size);
        try {
            checkMount();
            checkPath(path);

            if (size == 0) {
                long actualSize = this.storage.getTypeAndSize(prependMountName(path), this.lastBlockHash).payload().size();

                if (actualSize == 0) {
                    return SuccessCodes.OK;
                }

                //TODO: truncate file using a size
                this.lastBlockHash = this.storage.deleteFile(prependMountName(path), this.lastBlockHash).blockHash();
                this.lastBlockHash = this.storage.createFile(prependMountName(path), "", this.lastBlockHash).blockHash();

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
        }
    }

    @Override
    public int mkdir(String path, long mode) {
        LOGGER.debug("Called mkdir: {}", prependMountName(path));
        try {
            checkMount();
            checkPath(path);

            this.lastBlockHash = this.storage.addToParent(prependMountName(path), this.lastBlockHash).blockHash(); // fails if parent not found
            this.lastBlockHash = this.storage.createDir(prependMountName(path), this.lastBlockHash).blockHash();

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
        }
    }

    @Override
    public int readdir(String path, Pointer buf, FuseFillDir filter, long offset, FuseFileInfo fi) {
        LOGGER.debug("Called readdir: {}", prependMountName(path));
        try {
            Set<String> childs = this.storage.readDir(prependMountName(path), this.lastBlockHash).payload();

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
        }
    }

    @Override
    public int rename(String oldpath, String newpath) {
        LOGGER.debug("Called rename: {} -> {}", prependMountName(oldpath), prependMountName(newpath));
        try {
            checkMount();
            checkPath(oldpath);
            checkPath(newpath);

            appendAllChunks(prependMountName(oldpath));
            appendAllChunks(prependMountName(newpath));

            this.lastBlockHash = this.storage.addToParent(prependMountName(newpath), this.lastBlockHash).blockHash(); // fails if parent not found
            this.lastBlockHash = this.storage.removeFromParent(prependMountName(oldpath), this.lastBlockHash).blockHash(); // fails if parent not found
            this.lastBlockHash = this.storage.rename(prependMountName(oldpath), prependMountName(newpath), this.lastBlockHash).blockHash();

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
        }
    }

    @Override
    public int unlink(String path) {
        LOGGER.debug("Called unlink: {}", prependMountName(path));

        try {
            checkMount();
            checkPath(path);

            this.lastBlockHash = this.storage.deleteFile(prependMountName(path), this.lastBlockHash).blockHash();
            this.lastBlockHash = this.storage.removeFromParent(prependMountName(path), this.lastBlockHash).blockHash();

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

            this.lastBlockHash = this.storage.deleteDir(prependMountName(path), this.lastBlockHash).blockHash();
            this.lastBlockHash = this.storage.removeFromParent(prependMountName(path), this.lastBlockHash).blockHash();

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
        }
    }

    @Override
    public void mount(Path mountPoint, boolean blocking, boolean debug, String[] fuseOpts) {
        LOGGER.debug("Called mounting filesystem with mountPoint: {}", mountPoint.toString());

        if (this.mountPoint != null) {
            throw new FuseException("Already mounted");
        }

        synchronized (this) {

            generateMountName();
            this.storage = new F1r3flyFixedFSStorage(this.f1R3FlyApi, this.mountName);

            // root path
            try {
                this.lastBlockHash = ((F1r3flyFixedFSStorage) this.storage).initState(); // needed for F1r3flyFixedFSStorage only

                this.lastBlockHash = this.storage.createDir(this.mountName + ":" + PathUtils.getPathDelimiterBasedOnOS() + mountPoint.toAbsolutePath(), this.lastBlockHash)
                    .blockHash();

            } catch (NoDataByPath e) {
                LOGGER.warn("Directory not found", e);
                throw new FuseException("Directory not found", e);
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
            this.lastBlockHash = null;
            this.mountName = null;
        }
    }

    private void checkPath(String path) {
        if (path == null || !path.startsWith(PathUtils.getPathDelimiterBasedOnOS())) {
            throw new IllegalArgumentException("Invalid path: %s".formatted(path));
        }
    }

    private void checkMount() throws FuseException {
        if (this.mountPoint == null || this.lastBlockHash == null) {
            throw new FuseException("Not mounted");
        }
    }


    private String prependMountName(String path) {
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
