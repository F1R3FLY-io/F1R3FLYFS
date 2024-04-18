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

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Set;

public class F1r3flyFS extends FuseStubFS {

    private static final Logger LOGGER = LoggerFactory.getLogger(F1r3flyFS.class);

    private final F1r3flyApi f1R3FlyApi;
    private FSStorage storage;
    private String lastBlockHash;
    private String mountId;

    public F1r3flyFS(F1r3flyApi f1R3FlyApi) {
        super(); // no need to call Fuse constructor

        this.f1R3FlyApi = f1R3FlyApi;
    }

    @Override
    public int getattr(String path, FileStat stat) {
        LOGGER.info("Called getting attributes for path: {}", prependMountId(path));
        try {
            checkMount();
            checkPath(path);

            FSStorage.TypeAndSize typeAndSize = this.storage.getTypeAndSize(prependMountId(path), this.lastBlockHash).payload();

            LOGGER.info("Path {} is {}", prependMountId(path), typeAndSize.type());

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
                LOGGER.info("Not existing path {}", prependMountId(path));
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
        LOGGER.info("Called read: {}", prependMountId(path));
        try {
            checkMount();
            checkPath(path);

            String fileContent = this.storage.readFile(prependMountId(path), this.lastBlockHash).payload();

            buf.putString(offset, fileContent, (int) size, StandardCharsets.UTF_8);

            return fileContent.length();
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
        LOGGER.info("Called write file: {} with parameters size {} offset {} fi {}", prependMountId(path), size, offset, fi);
        try {
            checkMount();
            checkPath(path);

            Set<String> siblings = this.storage.readDir(prependMountId(PathUtils.getParentPath(path)), this.lastBlockHash).payload(); // check if path is a directory
            String filename = PathUtils.getFileName(path);
            if (!siblings.contains(filename)) {
                // no need to add to parent if already added
                this.lastBlockHash = this.storage.addToParent(prependMountId(path), this.lastBlockHash).blockHash();
            }

            // reads all data from jnr.ffi.Pointer `buff` into String `fileContent`
            String fileContent = buf.getString(offset, (int) size, StandardCharsets.UTF_8);

            this.lastBlockHash = this.storage.saveFile(prependMountId(path), fileContent, this.lastBlockHash).blockHash();

            return fileContent.length(); // return number of bytes written
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
        LOGGER.info("Called create file: {}", prependMountId(path));
        try {
            checkMount();
            checkPath(path);

            this.lastBlockHash = this.storage.saveFile(prependMountId(path), "", this.lastBlockHash).blockHash();
            this.lastBlockHash = this.storage.addToParent(prependMountId(path), this.lastBlockHash).blockHash();

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
    public int mkdir(String path, long mode) {
        LOGGER.info("Called mkdir: {}", prependMountId(path));
        try {
            checkMount();
            checkPath(path);

            this.lastBlockHash = this.storage.addToParent(prependMountId(path), this.lastBlockHash).blockHash(); // fails if parent not found
            this.lastBlockHash = this.storage.createDir(prependMountId(path), this.lastBlockHash).blockHash();

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
        LOGGER.info("Called readdir: {}", prependMountId(path));
        try {
            Set<String> childs = this.storage.readDir(prependMountId(path), this.lastBlockHash).payload();

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
        LOGGER.info("Called rename: {} -> {}", prependMountId(oldpath), prependMountId(newpath));
        try {
            checkMount();
            checkPath(oldpath);
            checkPath(newpath);

            this.lastBlockHash = this.storage.addToParent(prependMountId(newpath), this.lastBlockHash).blockHash(); // fails if parent not found
            this.lastBlockHash = this.storage.removeFromParent(prependMountId(oldpath), this.lastBlockHash).blockHash(); // fails if parent not found
            this.lastBlockHash = this.storage.rename(prependMountId(oldpath), prependMountId(newpath), this.lastBlockHash).blockHash();

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
        LOGGER.info("Called unlink: {}", prependMountId(path));

        try {
            checkMount();
            checkPath(path);

            this.lastBlockHash = this.storage.deleteFile(prependMountId(path), this.lastBlockHash).blockHash();
            this.lastBlockHash = this.storage.removeFromParent(prependMountId(path), this.lastBlockHash).blockHash();

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
        LOGGER.info("Called rmdir: {}", prependMountId(path));

        try {
            checkMount();
            checkPath(path);

            this.lastBlockHash = this.storage.deleteDir(prependMountId(path), this.lastBlockHash).blockHash();
            this.lastBlockHash = this.storage.removeFromParent(prependMountId(path), this.lastBlockHash).blockHash();

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

        if (this.mountId != null) {
            throw new FuseException("Already mounted");
        }

        synchronized (this) {
            generateMountId();

            this.storage = new F1r3flyFixedFSStorage(this.f1R3FlyApi, this.mountId);

            // root path
            try {
                this.lastBlockHash = ((F1r3flyFixedFSStorage) this.storage).initState(); // needed for F1r3flyFixedFSStorage only

                this.lastBlockHash = this.storage.createDir(this.mountId + mountPoint.toAbsolutePath(), this.lastBlockHash)
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
            // call super method to mount
            super.mount(mountPoint, blocking, debug, fuseOpts);
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
            this.mountId = null;
            this.lastBlockHash = null;
        }
    }

    private void checkPath(String path) {
        if (path == null || !path.startsWith(PathUtils.getPathDelimiterBasedOnOS())) {
            throw new IllegalArgumentException("Invalid path: %s".formatted(path));
        }
    }

    private void checkMount() throws FuseException {
        if (this.mountPoint == null || this.lastBlockHash == null || this.mountId == null) {
            throw new FuseException("Not mounted");
        }
    }

    private void generateMountId() {
        String pathDelimiter = PathUtils.getPathDelimiterBasedOnOS();

        // example: f1r3flyfs://123123123
        this.mountId = "f1r3flyfs:" + pathDelimiter + pathDelimiter + System.currentTimeMillis();
    }


    private String prependMountId(String path) {
        // example: f1r3flyfs://123123123/mounted-path/path-to-file
        String fullPath = this.mountId + this.mountPoint.toAbsolutePath() + path;

        if (fullPath.endsWith(PathUtils.getPathDelimiterBasedOnOS())) {
            fullPath = fullPath.substring(0, fullPath.length() - 1);
        }

        return fullPath;
    }

}
