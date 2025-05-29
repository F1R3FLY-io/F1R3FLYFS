package io.f1r3fly.fs.examples.storage.inmemory;

import io.f1r3fly.fs.FuseFillDir;
import io.f1r3fly.fs.examples.storage.DeployDispatcher;
import io.f1r3fly.fs.examples.storage.FileSystem;
import io.f1r3fly.fs.examples.storage.errors.*;
import io.f1r3fly.fs.examples.storage.grcp.F1r3flyApi;
import io.f1r3fly.fs.examples.storage.inmemory.common.IDirectory;
import io.f1r3fly.fs.examples.storage.inmemory.common.IFile;
import io.f1r3fly.fs.examples.storage.inmemory.common.IPath;
import io.f1r3fly.fs.examples.storage.inmemory.deployable.InMemoryDirectory;
import io.f1r3fly.fs.examples.storage.inmemory.deployable.InMemoryFile;
import io.f1r3fly.fs.examples.storage.inmemory.deployable.RemountedDirectory;
import io.f1r3fly.fs.examples.storage.inmemory.deployable.RemountedFile;
import io.f1r3fly.fs.examples.storage.rholang.RholangExpressionConstructor;
import io.f1r3fly.fs.struct.FileStat;
import io.f1r3fly.fs.struct.FuseContext;
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
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class InMemoryFileSystem implements FileSystem {

    private static final Logger LOGGER = LoggerFactory.getLogger(InMemoryFileSystem.class);

    private InMemoryDirectory rootDirectory;
    private DeployDispatcher deployDispatcher;
    private F1r3flyApi f1R3FlyApi;
    private String mountName;

    public InMemoryFileSystem(InMemoryDirectory rootDirectory, DeployDispatcher deployDispatcher, F1r3flyApi f1R3FlyApi, String mountName) {
        this.rootDirectory = rootDirectory;
        this.deployDispatcher = deployDispatcher;
        this.f1R3FlyApi = f1R3FlyApi;
        this.mountName = mountName;
    }

    public InMemoryFileSystem() {
        this.rootDirectory = null;
        this.deployDispatcher = null;
        this.f1R3FlyApi = null;
        this.mountName = null;
    }

    // Path manipulation methods
    public String getLastComponent(String path) {
        while (path.endsWith(PathUtils.getPathDelimiterBasedOnOS())) {
            path = path.substring(0, path.length() - 1);
        }
        if (path.isEmpty()) {
            return "";
        }
        return path.substring(path.lastIndexOf(PathUtils.getPathDelimiterBasedOnOS()) + 1);
    }

    public IDirectory getParentDirectory(String path) {
        String parentPath = path.substring(0, path.lastIndexOf("/"));

        try {
            IPath parent = getPath(parentPath);

            if (!(parent instanceof IDirectory)) {
                throw new IllegalArgumentException("Parent path is not a directory: " + parentPath);
            }
            return (IDirectory) parent;
        } catch (PathNotFound e) {
            return null;
        }
    }

    private IDirectory getParentDirectoryInternal(String path) throws PathNotFound {
        String parentPath = path.substring(0, path.lastIndexOf("/"));

        IPath parent = getPath(parentPath);

        if (!(parent instanceof IDirectory)) {
            throw new IllegalArgumentException("Parent path is not a directory: " + parentPath);
        }
        return (IDirectory) parent;
    }

    public IPath findPath(String path) {
        if (rootDirectory == null) {
            return null;
        }
        return rootDirectory.find(path);
    }

    public IPath getPath(String path) throws PathNotFound {
        IPath element = findPath(path);

        if (element == null) {
            throw new PathNotFound(path);
        }

        return element;
    }

    public IDirectory getDirectoryByPath(String path) throws PathNotFound, PathIsNotADirectory {
        IPath element = findPath(path);

        if (element == null) {
            throw new PathNotFound(path);
        }

        if (!(element instanceof IDirectory)) {
            throw new PathIsNotADirectory(path);
        }

        return (IDirectory) element;
    }

    public IFile getFileByPath(String path) throws PathNotFound, PathIsNotAFile {
        IPath element = findPath(path);

        if (element == null) {
            throw new PathNotFound(path);
        }

        if (!(element instanceof IFile)) {
            throw new PathIsNotAFile(path);
        }

        return (IFile) element;
    }

    // Core file system operations
    public void createFile(String path, @mode_t long mode) throws PathNotFound, FileAlreadyExists, OperationNotPermitted {
        IPath maybeExist = findPath(path);

        if (maybeExist != null) {
            throw new FileAlreadyExists(path);
        }

        IDirectory parent = getParentDirectoryInternal(path);
        parent.mkfile(getLastComponent(path));
    }

    public void getAttributes(String path, FileStat stat, FuseContext fuseContext) throws PathNotFound {
        IPath p = findPath(path);
        if (p == null) {
            throw new PathNotFound(path);
        }
        p.getAttr(stat, fuseContext);
    }

    public void makeDirectory(String path, @mode_t long mode) throws PathNotFound, FileAlreadyExists, OperationNotPermitted {
        IPath maybeExist = findPath(path);
        if (maybeExist != null) {
            throw new FileAlreadyExists(path);
        }

        IDirectory parent = getParentDirectoryInternal(path);
        parent.mkdir(getLastComponent(path));
    }

    public int readFile(String path, Pointer buf, @size_t long size, @off_t long offset) throws PathNotFound, PathIsNotAFile, IOException {
        IFile file = getFileByPath(path);
        return file.read(buf, size, offset);
    }

    public void readDirectory(String path, Pointer buf, FuseFillDir filter) throws PathNotFound, PathIsNotADirectory {
        IDirectory directory = getDirectoryByPath(path);
        directory.read(buf, filter);
    }

    public void getFileSystemStats(String path, Statvfs stbuf) {
        if ("/".equals(path)) {
            int BLOCKSIZE = 4096;
            int FUSE_NAME_MAX = 255;

            long totalSpace = 100L * 1024 * 1024 * 1024;
            long UsableSpace = totalSpace;
            long tBlocks = totalSpace / BLOCKSIZE;
            long aBlocks = UsableSpace / BLOCKSIZE;
            stbuf.f_bsize.set(BLOCKSIZE);
            stbuf.f_frsize.set(BLOCKSIZE);
            stbuf.f_blocks.set(tBlocks);
            stbuf.f_bavail.set(aBlocks);
            stbuf.f_bfree.set(aBlocks);
            stbuf.f_namemax.set(FUSE_NAME_MAX);
        }
    }

    public void renameFile(String path, String newName) throws PathNotFound, OperationNotPermitted {
        IPath p = getPath(path);
        IDirectory newParent = getParentDirectoryInternal(newName);

        IDirectory oldParent = p.getParent();
        p.rename(newName.substring(newName.lastIndexOf(PathUtils.getPathDelimiterBasedOnOS()) + 1), newParent);

        if (oldParent != newParent) {
            newParent.addChild(p);
            oldParent.deleteChild(p);
        } else {
            newParent.addChild(p);
        }

        if (p instanceof InMemoryFile) {
            ((InMemoryFile) p).onChange();
        }
    }

    public void removeDirectory(String path) throws PathNotFound, PathIsNotADirectory, DirectoryNotEmpty, OperationNotPermitted {
        IDirectory directory = getDirectoryByPath(path);
        if (!directory.isEmpty()) {
            throw new DirectoryNotEmpty(path);
        }
        directory.delete();
        IDirectory parent = directory.getParent();
        if (parent != null) {
            parent.deleteChild(directory);
        }
    }

    public void truncateFile(String path, long offset) throws PathNotFound, PathIsNotAFile, IOException {
        IFile file = getFileByPath(path);
        file.truncate(offset);
    }

    public void unlinkFile(String path) throws PathNotFound, OperationNotPermitted {
        IPath p = getPath(path);
        p.delete();
        IDirectory parent = p.getParent();
        if (parent != null) {
            parent.deleteChild(p);
        }
    }

    public void openFile(String path) throws PathNotFound, PathIsNotAFile, IOException {
        IFile file = getFileByPath(path);
        file.open();
    }

    public int writeFile(String path, Pointer buf, @size_t long size, @off_t long offset) throws PathNotFound, PathIsNotAFile, IOException {
        IFile file = getFileByPath(path);
        return file.write(buf, size, offset);
    }

    public void flushFile(String path) throws PathNotFound, PathIsNotAFile {
        IFile file = getFileByPath(path);
        file.close();
    }

    // Utility methods
    public boolean isAppleMetadataFile(String path) {
        return path.contains(".DS_Store") || path.contains("._.");
    }

    public String prependMountName(String path) {
        return this.mountName + path;
    }

    public boolean isMounted() {
        return this.rootDirectory != null;
    }

    // Shard operations
    public IPath fetchDirectoryFromShard(String absolutePath, String name, InMemoryDirectory parent) throws NoDataByPath {
        try {
            List<RhoTypes.Par> pars = f1R3FlyApi.findDataByName(absolutePath);

            RholangExpressionConstructor.ChannelData fileOrDir = RholangExpressionConstructor.parseChannelData(pars);

            if (fileOrDir.isDir()) {
                RemountedDirectory dir = new RemountedDirectory(this.mountName, name, parent, this.deployDispatcher);

                Set<IPath> children = fileOrDir.children().stream().map((childName) -> {
                    try {
                        return fetchDirectoryFromShard(absolutePath + PathUtils.getPathDelimiterBasedOnOS() + childName, childName, dir);
                    } catch (NoDataByPath e) {
                        LOGGER.error("Error fetching child directory from shard for path: {}",
                            absolutePath + PathUtils.getPathDelimiterBasedOnOS() + childName, e);
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

    // Setters for initialization
    public void setRootDirectory(InMemoryDirectory rootDirectory) {
        this.rootDirectory = rootDirectory;
    }

    public void setDeployDispatcher(DeployDispatcher deployDispatcher) {
        this.deployDispatcher = deployDispatcher;
    }

    public void setF1R3FlyApi(F1r3flyApi f1R3FlyApi) {
        this.f1R3FlyApi = f1R3FlyApi;
    }

    public void setMountName(String mountName) {
        this.mountName = mountName;
    }

    // FileSystem interface implementation
    @Override
    public IPath getRootPath() {
        return rootDirectory;
    }

    @Override
    public IFile getFile(String path) {
        try {
            return getFileByPath(path);
        } catch (PathNotFound | PathIsNotAFile e) {
            return null;
        }
    }

    @Override
    public IDirectory getDirectory(String path) {
        try {
            return getDirectoryByPath(path);
        } catch (PathNotFound | PathIsNotADirectory e) {
            return null;
        }
    }

    @Override
    public boolean isRootPath(String path) {
        return "/".equals(path) || "".equals(path);
    }

    @Nullable
    @Override
    public String getParentPath(String path) {
        if (isRootPath(path)) {
            return null;
        }
        int lastSlash = path.lastIndexOf("/");
        if (lastSlash <= 0) {
            return "/";
        }
        return path.substring(0, lastSlash);
    }
}
