package io.f1r3fly.fs.examples.storage.filesystem;

import casper.DeployServiceCommon;
import io.f1r3fly.fs.FuseFillDir;
import io.f1r3fly.fs.examples.storage.DeployDispatcher;
import io.f1r3fly.fs.examples.storage.FileSystem;
import io.f1r3fly.fs.examples.storage.errors.*;
import io.f1r3fly.fs.examples.storage.grcp.F1r3flyApi;
import io.f1r3fly.fs.examples.storage.filesystem.common.Directory;
import io.f1r3fly.fs.examples.storage.filesystem.common.File;
import io.f1r3fly.fs.examples.storage.filesystem.common.Path;
import io.f1r3fly.fs.examples.storage.filesystem.deployable.BlockchainFile;
import io.f1r3fly.fs.examples.storage.filesystem.deployable.UnlockedWalletDirectory;
import io.f1r3fly.fs.examples.storage.filesystem.local.LockedRemoteDirectory;
import io.f1r3fly.fs.examples.storage.filesystem.local.RootDirectory;
import io.f1r3fly.fs.examples.storage.filesystem.local.TokenDirectory;
import io.f1r3fly.fs.examples.storage.filesystem.local.TokenFile;
import io.f1r3fly.fs.struct.FileStat;
import io.f1r3fly.fs.struct.FuseContext;
import io.f1r3fly.fs.struct.Statvfs;
import io.f1r3fly.fs.utils.PathUtils;
import jnr.ffi.Pointer;
import jnr.ffi.types.mode_t;
import jnr.ffi.types.off_t;
import jnr.ffi.types.size_t;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class InMemoryFileSystem implements FileSystem {

    private static final Logger logger = LoggerFactory.getLogger(InMemoryFileSystem.class);

    @NotNull
    private final RootDirectory rootDirectory;
    @NotNull
    private final DeployDispatcher deployDispatcher;

    public InMemoryFileSystem(F1r3flyApi f1r3flyApi) {
        this.deployDispatcher = new DeployDispatcher(f1r3flyApi);
        Set<Path> lockedRemoteDirectories = createRavAddressDirectories(this.deployDispatcher);
        this.rootDirectory = new RootDirectory(lockedRemoteDirectories);
    }

    // Helper method to get path separator
    private String pathSeparator() {
        return PathUtils.getPathDelimiterBasedOnOS();
    }

    // Simplified path manipulation methods
    public String getLastComponent(String path) {
        // Remove trailing separators
        while (path.endsWith(pathSeparator()) && path.length() > 1) {
            path = path.substring(0, path.length() - 1);
        }
        
        if (path.equals(pathSeparator())) {
            return pathSeparator();
        }
        
        int lastSeparatorIndex = path.lastIndexOf(pathSeparator());
        return lastSeparatorIndex == -1 ? path : path.substring(lastSeparatorIndex + 1);
    }

    public Directory getParentDirectory(String path) {
        String parentPath = getParentPath(path);
        if (parentPath == null) {
            return null;
        }

        try {
            Path parent = getPath(parentPath);
            if (!(parent instanceof Directory)) {
                throw new IllegalArgumentException("Parent path is not a directory: " + parentPath);
            }
            return (Directory) parent;
        } catch (PathNotFound e) {
            return null;
        }
    }

    private Directory getParentDirectoryInternal(String path) throws PathNotFound {
        String parentPath = getParentPath(path);
        if (parentPath == null) {
            throw new PathNotFound("No parent for root path: " + path);
        }

        Path parent = getPath(parentPath);
        if (!(parent instanceof Directory)) {
            throw new IllegalArgumentException("Parent path is not a directory: " + parentPath);
        }
        return (Directory) parent;
    }

    public Path findPath(String path) {
        return this.rootDirectory.find(path);
    }

    public Path getPath(String path) throws PathNotFound {
        Path element = findPath(path);

        if (element == null) {
            throw new PathNotFound(path);
        }

        return element;
    }

    public Directory getDirectoryByPath(String path) throws PathNotFound, PathIsNotADirectory {
        Path element = findPath(path);

        if (element == null) {
            throw new PathNotFound(path);
        }

        if (!(element instanceof Directory)) {
            throw new PathIsNotADirectory(path);
        }

        return (Directory) element;
    }

    public File getFileByPath(String path) throws PathNotFound, PathIsNotAFile {
        Path element = findPath(path);

        if (element == null) {
            throw new PathNotFound(path);
        }

        if (!(element instanceof File)) {
            throw new PathIsNotAFile(path);
        }

        return (File) element;
    }

    // Core file system operations
    public void createFile(String path, @mode_t long mode)
            throws PathNotFound, FileAlreadyExists, OperationNotPermitted {
        Path maybeExist = findPath(path);

        if (maybeExist != null) {
            throw new FileAlreadyExists(path);
        }

        Directory parent = getParentDirectoryInternal(path);
        parent.mkfile(getLastComponent(path));
    }

    public void getAttributes(String path, FileStat stat, FuseContext fuseContext) throws PathNotFound {
        Path p = findPath(path);
        if (p == null) {
            throw new PathNotFound(path);
        }
        p.getAttr(stat, fuseContext);
    }

    public void makeDirectory(String path, @mode_t long mode)
            throws PathNotFound, FileAlreadyExists, OperationNotPermitted {
        Path maybeExist = findPath(path);
        if (maybeExist != null) {
            throw new FileAlreadyExists(path);
        }

        Directory parent = getParentDirectoryInternal(path);
        parent.mkdir(getLastComponent(path));
    }

    public int readFile(String path, Pointer buf, @size_t long size, @off_t long offset)
            throws PathNotFound, PathIsNotAFile, IOException {
        File file = getFileByPath(path);
        return file.read(buf, size, offset);
    }

    public void readDirectory(String path, Pointer buf, FuseFillDir filter) throws PathNotFound, PathIsNotADirectory {
        Directory directory = getDirectoryByPath(path);
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
        Path p = getPath(path);
        Directory newParent = getParentDirectoryInternal(newName);

        Directory oldParent = p.getParent();
        p.rename(getLastComponent(newName), newParent);

        if (oldParent != newParent) {
            newParent.addChild(p);
            oldParent.deleteChild(p);
        } else {
            newParent.addChild(p);
        }

        if (p instanceof BlockchainFile) {
            ((BlockchainFile) p).onChange();
        }
    }

    public void removeDirectory(String path)
            throws PathNotFound, PathIsNotADirectory, DirectoryNotEmpty, OperationNotPermitted {
        Directory directory = getDirectoryByPath(path);
        if (!directory.isEmpty()) {
            throw new DirectoryNotEmpty(path);
        }
        directory.delete();
        Directory parent = directory.getParent();
        if (parent != null) {
            parent.deleteChild(directory);
        }
    }

    public void truncateFile(String path, long offset) throws PathNotFound, PathIsNotAFile, IOException {
        File file = getFileByPath(path);
        file.truncate(offset);
    }

    public void unlinkFile(String path) throws PathNotFound, OperationNotPermitted {
        Path p = getPath(path);
        p.delete();
        Directory parent = p.getParent();
        if (parent != null) {
            parent.deleteChild(p);
        }
    }

    public void openFile(String path) throws PathNotFound, PathIsNotAFile, IOException {
        File file = getFileByPath(path);
        file.open();
    }

    public int writeFile(String path, Pointer buf, @size_t long size, @off_t long offset)
            throws PathNotFound, PathIsNotAFile, IOException {
        File file = getFileByPath(path);
        return file.write(buf, size, offset);
    }

    public void flushFile(String path) throws PathNotFound, PathIsNotAFile {
        File file = getFileByPath(path);
        file.close();
    }

    // Utility methods
    @Override
    public File getFile(String path) {
        try {
            return getFileByPath(path);
        } catch (PathNotFound | PathIsNotAFile e) {
            return null;
        }
    }

    @Override
    public Directory getDirectory(String path) {
        try {
            return getDirectoryByPath(path);
        } catch (PathNotFound | PathIsNotADirectory e) {
            return null;
        }
    }

    @Override
    public boolean isRootPath(String path) {
        return pathSeparator().equals(path) || "".equals(path);
    }

    @Nullable
    @Override
    public String getParentPath(String path) {
        if (isRootPath(path)) {
            return null;
        }
        
        int lastSeparatorIndex = path.lastIndexOf(pathSeparator());
        if (lastSeparatorIndex <= 0) {
            return pathSeparator();
        }
        return path.substring(0, lastSeparatorIndex);
    }

    private List<String> parseRavAddressesFromGenesisBlock(F1r3flyApi f1R3FlyApi) {
        List<DeployServiceCommon.DeployInfo> deploys = f1R3FlyApi.getGenesisBlock().getDeploysList();

        DeployServiceCommon.DeployInfo tokenInitializeDeploy = deploys.stream()
                .filter((deployInfo1 -> deployInfo1.getTerm().contains("revVaultInitCh"))).findFirst().orElseThrow();

        String regex = "\\\"(1111[A-Za-z0-9]+)\\\"";
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(regex);
        java.util.regex.Matcher matcher = pattern.matcher(tokenInitializeDeploy.getTerm());

        List<String> ravAddresses = new java.util.ArrayList<>();
        while (matcher.find()) {
            ravAddresses.add(matcher.group(1));
        }

        return ravAddresses;
    }

    private Set<Path> createRavAddressDirectories(DeployDispatcher deployDispatcher) {
        List<String> ravAddresses = parseRavAddressesFromGenesisBlock(deployDispatcher.getF1R3FlyApi());

        logger.debug("Addresses found in genesis block: {}", ravAddresses);

        Set<Path> children = new HashSet<>();

        for (String address : ravAddresses) {
            children.add(new LockedRemoteDirectory(address));
        }

        return children;
    }

    public void unlockRootDirectory(String revAddress, String privateKey) {
        String searchPath = "/LOCKED-REMOTE-REV-" + revAddress;
        logger.debug("Attempting to unlock root directory with path: {}", searchPath);
        logger.debug("Root directory children: {}", 
            rootDirectory.getChildren().stream()
                .map(Path::getName)
                .collect(java.util.stream.Collectors.toList()));
        
        Path lockedRoot = getDirectory(searchPath);

        if (lockedRoot instanceof LockedRemoteDirectory) {
            try {
                UnlockedWalletDirectory unlockedRoot = ((LockedRemoteDirectory) lockedRoot).unlock(privateKey,
                        deployDispatcher);

                this.rootDirectory.deleteChild(lockedRoot);
                this.rootDirectory.addChild(unlockedRoot);
            } catch (OperationNotPermitted e) {
                logger.warn("Failed to unlock root directory: {}", revAddress, e);
            }

        } else {
            logger.warn("Root directory is not locked: {}", revAddress);
            logger.warn("Root directory: {}", lockedRoot);
            if (lockedRoot != null) {
                logger.warn("Root directory type: {}", lockedRoot.getClass());
                logger.warn("Root directory name: {}", lockedRoot.getName());
                logger.warn("Root directory parent: {}", lockedRoot.getParent());
            } else {
                logger.warn("Root directory is null - path not found: /{}", revAddress);
            }
        }
    }

    @Override
    public void exchangeTokenFile(String filePath) throws NoDataByPath {
        File file = getFile(filePath);
        if (file == null) {
            throw new NoDataByPath(filePath);
        }

        if (!(file instanceof TokenFile)) {
            throw new RuntimeException("File is not a token file: " + filePath);
        }

        TokenFile tokenFile = (TokenFile) file;
        Directory tokenDirectory = tokenFile.getParent();

        if (tokenDirectory == null) {
            throw new RuntimeException("Token directory is null: " + filePath);
        }

        if (!(tokenDirectory instanceof TokenDirectory)) {
            throw new RuntimeException("Token directory is not a token directory: " + filePath);
        }

        ((TokenDirectory) tokenDirectory).exchange(tokenFile);
    }

    @Override
    public void waitOnBackgroundDeploy() {
        deployDispatcher.waitOnEmptyQueue();
    }

    @Override
    public void terminate() {
        waitOnBackgroundDeploy();
        this.deployDispatcher.destroy();
        this.rootDirectory.cleanLocalCache();
    }
} 