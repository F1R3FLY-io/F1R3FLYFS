package io.f1r3fly.fs.examples;

import io.f1r3fly.fs.*;
import io.f1r3fly.fs.examples.datatransformer.AESCipher;
import io.f1r3fly.fs.examples.storage.F1f3flyFSStorage;
import io.f1r3fly.fs.examples.storage.F1r3flyFixedFSStorage;
import io.f1r3fly.fs.examples.storage.FSStorage;
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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

public class F1r3flyFS extends FuseStubFS {

    private static final Logger LOGGER = LoggerFactory.getLogger(F1r3flyFS.class);

    private final F1r3flyApi f1R3FlyApi;
    private final AESCipher aesCipher;
    private FSStorage storage;
    private String lastBlockHash;

    // it should be a number that can be divisible by
    // * 3 because of padding of base64
    // * 16 because of AES block size
    private final int MAX_CHUNK_SIZE = 3 * 16 * 1024 * 1024; // 48 MB

    private final String[] MOUNT_OPTIONS = {
        // refers to https://github.com/osxfuse/osxfuse/wiki/Mount-options#iosize
        "-o", "noappledouble"
    };

    private final ConcurrentHashMap<String, ByteBuffer> writingCache;
    private String lastReadFilePath;
    private byte[] lastReadFileData;

    public F1r3flyFS(F1r3flyApi f1R3FlyApi, AESCipher aesCipher) {
        super(); // no need to call Fuse constructor

        this.f1R3FlyApi = f1R3FlyApi;
        this.writingCache = new ConcurrentHashMap<>();
        this.aesCipher = aesCipher;
    }

    private synchronized void cacheAndAppendChunk(String path, byte[] data, long offset) throws F1r3flyDeployError, IOException {
        ByteBuffer cached = writingCache.get(path);
        if (cached == null) {
            cached = ByteBuffer.allocate(MAX_CHUNK_SIZE);
            writingCache.put(path, cached);
        }

        // Add data to cache at the correct offset
        cached.position((int) offset);
        cached.put(data);

        if (cached.position() >= MAX_CHUNK_SIZE) {
            appendChunkNow(path);
        }

    }

    private void appendAllChunks(String path) throws F1r3flyDeployError {
        if (writingCache.containsKey(path)) {
            appendChunkNow(path);

            appendAllChunks(path);
        }

    }

    private synchronized void appendChunkNow(String path) throws F1r3flyDeployError {
        if (writingCache.containsKey(path)) {
            ByteBuffer cached = writingCache.get(path);
            cached.flip(); // Prepare the buffer to be read

            int chunkSize = Math.min(MAX_CHUNK_SIZE, cached.remaining());
            byte[] chunkToWrite = new byte[chunkSize];
            cached.get(chunkToWrite, 0, chunkSize);

            byte[] encryptedChunk = PathUtils.isEncryptedExtension(path) ? aesCipher.encrypt(chunkToWrite) : chunkToWrite;
            this.lastBlockHash = this.storage.appendFile(path, encryptedChunk, chunkToWrite.length, this.lastBlockHash).blockHash();

            if (!cached.hasRemaining()) {
                LOGGER.debug("Cache is empty, removing path: {}", path);
                writingCache.remove(path);

                if (PathUtils.isDeployableFile(path)) {
                    deployingFile(path);
                }
            } else {
                cached.compact(); // Compact the buffer for further writes
            }

            // Clear the read cache on "write" operation
            if (lastReadFileData != null && lastReadFilePath != null && lastReadFilePath.equals(path)) {
                lastReadFileData = null;
                lastReadFilePath = null;
            }
        }
    }

    private void deployingFile(String path) {
        LOGGER.debug("Deploying a file: {}", path);

        try {
            FSStorage.OperationResult<String> executionResult = this.storage.deployFile(path, this.lastBlockHash);
            this.lastBlockHash = executionResult.blockHash();
            this.lastBlockHash = this.storage.addToParent(executionResult.payload(), this.lastBlockHash).blockHash();
        } catch (NoDataByPath | PathIsNotAFile | PathIsNotADirectory | RuntimeException | DirectoryNotFound | F1r3flyDeployError e) {
            LOGGER.error("Internal error: can't deploy {}", path, e);
            throw new RuntimeException(e);
        }
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

            RholangExpressionConstructor.ChannelData dirOrFile =
                this.storage.getTypeAndSize(prependMountName(path), this.lastBlockHash).payload();

            LOGGER.info("Path {} is '{}' type and size is {}",
                prependMountName(path), dirOrFile.type(), dirOrFile.size());

            if (dirOrFile.isFile()) {

                ByteBuffer cached = writingCache.get(prependMountName(path));
                int size = (int) (cached == null ? dirOrFile.size() : dirOrFile.size() + cached.position());

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
    public int flush(String path, FuseFileInfo fi) {
        LOGGER.debug("Called flush: {}", prependMountName(path));
        if (lastReadFilePath != null && lastReadFilePath.equals(prependMountName(path))) {
            lastReadFilePath = null;
            lastReadFileData = null;
        }
        return SuccessCodes.OK;
    }

    @Override
    public synchronized int read(String path, Pointer buf, @size_t long size, @off_t long offset, FuseFileInfo fi) {
        LOGGER.debug("Called read: {}, size {}, offset {}", prependMountName(path), size, offset);
        try {
            checkMount();
            checkPath(path);

            byte[] fileData;

            // Read all data from Node if it's a new read or if the path has changed
            if (lastReadFilePath == null || !lastReadFilePath.equals(prependMountName(path))) {
                LOGGER.debug("Read all data from Node");
                byte[] fileContent = this.storage.readFile(prependMountName(path), this.lastBlockHash).payload();
                lastReadFileData = PathUtils.isEncryptedExtension(path) ? aesCipher.decrypt(fileContent) : fileContent;
                lastReadFilePath = prependMountName(path);
            }

            // Combine file data with cache data if cache is not empty
            ByteBuffer writeCache = writingCache.get(prependMountName(path));
            if (writeCache != null && writeCache.position() > 0) {
                byte[] writeCacheData = new byte[writeCache.position()];
                writeCache.flip();
                writeCache.get(writeCacheData);

                ByteBuffer bb = ByteBuffer.allocate(lastReadFileData.length + writeCacheData.length);
                bb.put(lastReadFileData);
                bb.put(writeCacheData);
                fileData = bb.array();
            } else {
                fileData = lastReadFileData;
            }

            // Slice the buffer to the size
            int end = (int) Math.min(fileData.length, offset + size);
            byte[] sliced = Arrays.copyOfRange(fileData, (int) offset, end);
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
    public synchronized int write(String path, Pointer buf, @size_t long size, @off_t long offset, FuseFileInfo fi) {
        LOGGER.debug("Called write file: {} with parameters size {} offset {}", prependMountName(path), size, offset);
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

            // Write the data directly to cache
            cacheAndAppendChunk(prependMountName(path), read, offset);

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

            this.lastBlockHash = this.storage.createFile(prependMountName(path), new byte[0], 0, this.lastBlockHash).blockHash();
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

            if (size == 0) { // support the truncate to zero only for now
                long actualSize = this.storage.getTypeAndSize(prependMountName(path), this.lastBlockHash).payload().size();

                if (actualSize == 0) {
                    return SuccessCodes.OK;
                }

                //TODO: truncate file using a size
                this.lastBlockHash = this.storage.deleteFile(prependMountName(path), this.lastBlockHash).blockHash();
                this.lastBlockHash = this.storage.createFile(prependMountName(path), new byte[0], 0, this.lastBlockHash).blockHash();

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

            if (PathUtils.isEncryptedExtension(oldpath) && !PathUtils.isEncryptedExtension(newpath)) {
                // was encrypted, now not encrypted
                byte[] encrypted = this.storage.readFile(prependMountName(oldpath), this.lastBlockHash).payload();
                byte[] decrypted = aesCipher.decrypt(encrypted);
                this.lastBlockHash = this.storage.createFile(prependMountName(newpath), decrypted, decrypted.length, this.lastBlockHash).blockHash();
                this.lastBlockHash = this.storage.deleteFile(prependMountName(oldpath), this.lastBlockHash).blockHash();
            } else if (PathUtils.isEncryptedExtension(newpath) && !PathUtils.isEncryptedExtension(oldpath)) {
                // was not encrypted, now encrypted
                byte[] notEncrypted = this.storage.readFile(prependMountName(oldpath), this.lastBlockHash).payload();
                byte[] encrypted = aesCipher.encrypt(notEncrypted);
                this.lastBlockHash = this.storage.createFile(prependMountName(newpath), encrypted, notEncrypted.length, this.lastBlockHash).blockHash();
                this.lastBlockHash = this.storage.deleteFile(prependMountName(oldpath), this.lastBlockHash).blockHash();
            } else {
                // just rename
                this.lastBlockHash = this.storage.rename(prependMountName(oldpath), prependMountName(newpath), this.lastBlockHash).blockHash();
            }

            if (PathUtils.isDeployableFile(newpath)) {
                deployingFile(prependMountName(newpath));
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
        } catch (Exception e) {
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

        if (this.mounted.get()) {
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


    // public because of this method is used in tests
    public String getMountName() {
        return mountName;
    }

    // public because of this method is used in tests
    public String getLastBlockHash() {
        return lastBlockHash;
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
