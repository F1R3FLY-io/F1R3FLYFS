package io.f1r3fly.fs.examples;

import io.f1r3fly.fs.*;
import io.f1r3fly.fs.examples.storage.F1f3flyFSStorage;
import io.f1r3fly.fs.examples.storage.F1r3flyFixedFSStorage;
import io.f1r3fly.fs.examples.storage.FSStorage;
import io.f1r3fly.fs.examples.storage.errors.*;
import io.f1r3fly.fs.FuseException;
import io.f1r3fly.fs.examples.storage.grcp.F1r3flyApi;
import io.f1r3fly.fs.struct.FileStat;
import io.f1r3fly.fs.struct.FuseFileInfo;
import io.f1r3fly.fs.utils.PathUtils;
import jnr.ffi.Pointer;
import jnr.ffi.types.off_t;
import jnr.ffi.types.size_t;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

public class F1r3flyFS extends FuseStubFS {

    private static final Logger LOGGER = LoggerFactory.getLogger(F1r3flyFS.class);

    private final F1r3flyApi f1R3FlyApi;
    private FSStorage storage;
    private String lastBlockHash;

    private final int IOSIZE = 512;
    private final String[] MOUNT_OPTIONS = {
        // refers to https://github.com/osxfuse/osxfuse/wiki/Mount-options#iosize
        "-o", "iosize=" + IOSIZE,
        "-o", "noappledouble"
    };

    public F1r3flyFS(F1r3flyApi f1R3FlyApi) {
        super(); // no need to call Fuse constructor

        this.f1R3FlyApi = f1R3FlyApi;
    }

    @Override
    public int getattr(String path, FileStat stat) {
        LOGGER.info("Called getting attributes for path: {}", prependMountName(path));
        try {
            checkMount();
            checkPath(path);

            FSStorage.TypeAndSize typeAndSize = this.storage.getTypeAndSize(prependMountName(path), this.lastBlockHash).payload();

            LOGGER.info("Path {} is {}", prependMountName(path), typeAndSize.type());

            if (typeAndSize.type().equals(F1f3flyFSStorage.FILE_TYPE)) {
                stat.st_mode.set(FileStat.S_IFREG | 0777);
                stat.st_uid.set(getContext().uid.get());
                stat.st_gid.set(getContext().gid.get());
                stat.st_size.set(typeAndSize.size());

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
        LOGGER.info("Called read: {}", prependMountName(path));
        try {
            checkMount();
            checkPath(path);

            String fileContent = this.storage.readFile(prependMountName(path), this.lastBlockHash).payload();

            byte[] decoded = Base64.getDecoder().decode(fileContent);
            buf.put(0, decoded, 0, decoded.length);

            return decoded.length;
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
        LOGGER.info("Called write file: {} with parameters size {} offset {} fi {}", prependMountName(path), size, offset, fi);
        try {
            checkMount();
            checkPath(path);

            Set<String> siblings = this.storage.readDir(prependMountName(PathUtils.getParentPath(path)), this.lastBlockHash).payload(); // check if path is a directory
            String filename = PathUtils.getFileName(path);
            if (!siblings.contains(filename)) {
                // no need to add to parent if already added
                this.lastBlockHash = this.storage.addToParent(prependMountName(path), this.lastBlockHash).blockHash();
            }

            byte[] read = new byte[(int) size];
            buf.get(0, read, 0, (int) size);
            String fileContent = Base64.getEncoder().encodeToString(read);

            LOGGER.info("APPEND: size of buffer {}, offset of buffer {}, length of a result string {}",
                size, offset, fileContent.length());

            this.lastBlockHash = this.storage.appendFile(prependMountName(path), fileContent, this.lastBlockHash).blockHash();

            return (int) size; // return number of bytes written
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
    public int create(String path, long mode, FuseFileInfo fi) {
        LOGGER.info("Called create file: {}", prependMountName(path));
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
        LOGGER.info("Called truncate file {} and size {}", prependMountName(path), size);
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
        LOGGER.info("Called mkdir: {}", prependMountName(path));
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
        LOGGER.info("Called readdir: {}", prependMountName(path));
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
        LOGGER.info("Called rename: {} -> {}", prependMountName(oldpath), prependMountName(newpath));
        try {
            checkMount();
            checkPath(oldpath);
            checkPath(newpath);

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
        LOGGER.info("Called unlink: {}", prependMountName(path));

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
        LOGGER.info("Called rmdir: {}", prependMountName(path));

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
        LOGGER.info("Called mounting filesystem with mountPoint: {}", mountPoint.toString());

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
        LOGGER.info("Called unmounting filesystem");

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
