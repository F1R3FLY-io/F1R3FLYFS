package io.f1r3fly.fs.examples.storage;

import io.f1r3fly.fs.examples.storage.errors.*;
import io.f1r3fly.fs.examples.storage.grcp.F1r3flyApi;
import io.f1r3fly.fs.examples.storage.rholang.RholangExpressionConstructor;
import io.f1r3fly.fs.utils.PathUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rhoapi.RhoTypes;

import java.util.*;

public class F1f3flyFSStorage implements FSStorage {

    private final F1r3flyApi f1R3FlyApi;

    private final Logger LOGGER = LoggerFactory.getLogger(this.getClass().getName());


    public F1f3flyFSStorage(F1r3flyApi f1R3FlyApi) {
        this.f1R3FlyApi = f1R3FlyApi;
    }

    @Override
    public OperationResult<RholangExpressionConstructor.ChannelData> getTypeAndSize(
        @NotNull String path,
        @NotNull String lastBlockHash) throws NoDataByPath {
        // gets data from `@"path/to/something"` channel
        List<RhoTypes.Par> response = this.f1R3FlyApi.getDataAtName(lastBlockHash, path);

        try {
            RholangExpressionConstructor.ChannelData parsed = RholangExpressionConstructor.parseChannelData(response);

            // block hash is the same because of no changes
            return new OperationResult<>(parsed, lastBlockHash);
        } catch (IllegalArgumentException e) {
            throw new NoDataByPath(path, lastBlockHash, e);
        }
    }

    @Override
    public OperationResult<Void> rename(
        @NotNull String oldPath,
        @NotNull String newPath,
        @NotNull String blockHash) throws NoDataByPath, F1r3flyDeployError, DirectoryNotFound, PathIsNotADirectory, DirectoryIsNotEmpty, AlreadyExists {

        synchronized (this) {
            // check if exists (fails if not found by old path)
            RholangExpressionConstructor.ChannelData fileOrDir = getTypeAndSize(oldPath, blockHash)
                .payload();

            try {
                // check if exists (fails if found by new path)
                getTypeAndSize(newPath, blockHash);
                throw new AlreadyExists(newPath);
            } catch (NoDataByPath e) {
                // ok
            }

            if (fileOrDir.isDir() && !fileOrDir.children().isEmpty()) {
                throw new DirectoryIsNotEmpty(oldPath);
            }

            String blockHashAfterRename = this.f1R3FlyApi.deploy(
                RholangExpressionConstructor.renameChanel(oldPath, newPath),
                true,
                F1r3flyApi.RHOLANG);

            // block hash is the same because of no changes
            return new OperationResult<>(null, blockHashAfterRename);
        }

    }

    @Override
    public OperationResult<Void> createFile(
        @NotNull String path,
        @NotNull byte [] content,
        long size,
        @NotNull String blockHash) throws F1r3flyDeployError {
        // TODO remove lastBlockHash, no need to pass it here

        synchronized (this) {
            String rholangExpression =
                RholangExpressionConstructor.sendFileIntoNewChanel(
                    path,
                    size,
                    content,
                    currentTime()
                );

            String newBlockHash = this.f1R3FlyApi.deploy(rholangExpression, false, F1r3flyApi.RHOLANG);

            return new OperationResult<>(null, newBlockHash);
        }
    }


    @Override
    public OperationResult<Void> appendFile(
        @NotNull String path,
        @NotNull byte [] content,
        long size,
        @NotNull String lastBlockHash) throws F1r3flyDeployError {
        // TODO remove lastBlockHash, no need to pass it here

        synchronized (this) {
            String rholangExpression =
                RholangExpressionConstructor.appendValue(
                    path,
                    currentTime(),
                    content,
                    size
                );

            String newBlockHash = this.f1R3FlyApi.deploy(rholangExpression, true, F1r3flyApi.RHOLANG);

            return new OperationResult<>(null, newBlockHash);
        }
    }

    private static @NotNull long currentTime() {
        return System.currentTimeMillis();
    }

    @Override
    public OperationResult<byte[]> readFile(
        @NotNull String path,
        @NotNull String lastBlockHash) throws NoDataByPath, PathIsNotAFile {

        // reads data from `@"path/to/something"` channel
        RholangExpressionConstructor.ChannelData chanelContent = getFile(path, lastBlockHash);

        return new OperationResult<>(chanelContent.fileContent(), lastBlockHash); // block hash is the same because of no changes
    }

    @Override
    public OperationResult<String> deployFile(
        @NotNull String path,
        @NotNull String blockHash) throws NoDataByPath, F1r3flyDeployError, PathIsNotAFile {
        if (!PathUtils.isDeployableFile(path)) {
            throw new PathIsNotAFile("The file is not Rho or Metta type", path);
        }

        synchronized (this) {
            byte[] fileContent = readFile(path, blockHash).payload();
            String rholangCode = new String(fileContent);

            boolean useBiggerRhloPrice = rholangCode.length() > 1000; // TODO: double check the number?

            String blockWithExecutedRholang = this.f1R3FlyApi.deploy(
                rholangCode,
                useBiggerRhloPrice,
                path.endsWith(".metta") ? F1r3flyApi.METTA_LANGUAGE : F1r3flyApi.RHOLANG);

            String blockHashFile = PathUtils.getFileName(path) + ".blockhash";
            String fullPathWithBlockHashFile =
                PathUtils.getParentPath(path)
                    + PathUtils.getPathDelimiterBasedOnOS()
                    + blockHashFile;

            byte[] encodedFileContent = blockWithExecutedRholang.getBytes();

            String newLastBlockHash =
                createFile(
                    fullPathWithBlockHashFile,
                    encodedFileContent,
                    blockWithExecutedRholang.length(),
                    blockHash).blockHash();

            return new OperationResult<>(fullPathWithBlockHashFile, newLastBlockHash);
        }

    }

    @Override
    public OperationResult<Void> deleteFile(
        @NotNull String path,
        @NotNull String lastBlockHash) throws NoDataByPath, F1r3flyDeployError, PathIsNotAFile {
        synchronized (this) {
            getFile(path, lastBlockHash); // check if file getType

            String newBlockHash =
                this.f1R3FlyApi.deploy(
                    RholangExpressionConstructor.readAndForget(path, currentTime()),
                    false,
                    F1r3flyApi.RHOLANG); // forges a data

            return new OperationResult<>(null, newBlockHash);
        }
    }

    @Override
    public OperationResult<Void> createDir(
        @NotNull String path,
        @NotNull String blockHash) throws F1r3flyDeployError {
        // TODO blockhash is not used here, remove

        synchronized (this) {
            // Rholang looks like `@"path/to/something"!({"type": "d", "children": "[]", "lastUpdated": 1234567890})`
            String rholangExpression =
                RholangExpressionConstructor.sendDirectoryIntoNewChannel(
                    path,
                    new HashSet<>(), // empty set of children
                    currentTime()
                );

            String newBlockHash = this.f1R3FlyApi.deploy(rholangExpression, false, F1r3flyApi.RHOLANG);

            return new OperationResult<>(null, newBlockHash);
        }
    }

    @Override
    public OperationResult<Set<String>> readDir(
        @NotNull String path,
        @NotNull String lastBlockHash) throws DirectoryNotFound, NoDataByPath, PathIsNotADirectory {
        // reads data from `@"path/to/something"` channel
        RholangExpressionConstructor.ChannelData channelContent = getDir(path, lastBlockHash);


        return new OperationResult<>(channelContent.children(), lastBlockHash); // block hash is the same because of no changes
    }

    @Override
    public OperationResult<Void> deleteDir(
        @NotNull String path,
        @NotNull String lastBlockHash) throws PathIsNotADirectory, DirectoryNotFound, F1r3flyDeployError, DirectoryIsNotEmpty {
        synchronized (this) {
            RholangExpressionConstructor.ChannelData dir = getDir(path, lastBlockHash);

            if (!dir.children().isEmpty()) {
                throw new DirectoryIsNotEmpty(path);
            }

            String newBlockHash =
                this.f1R3FlyApi.deploy(
                    RholangExpressionConstructor.readAndForget(path, currentTime()),
                    false,
                    F1r3flyApi.RHOLANG); // forgets a data

            return new OperationResult<>(null, newBlockHash);
        }
    }


    public OperationResult<Void> addToParent(
        @NotNull String path,
        @NotNull String lastBlockHash) throws PathIsNotADirectory, F1r3flyDeployError, DirectoryNotFound {

        synchronized (this) {

            String parentPath = PathUtils.getParentPath(path);
            String newChild = PathUtils.getFileName(path);

            RholangExpressionConstructor.ChannelData dir = getDir(parentPath, lastBlockHash);

            dir.children().add(newChild);

            String rholangExpression =
                RholangExpressionConstructor.updateChildren(
                    parentPath,
                    dir.children(),
                    currentTime()
                );

            String newBlockHash = this.f1R3FlyApi.deploy(rholangExpression, false, F1r3flyApi.RHOLANG);

            return new OperationResult<>(null, newBlockHash);
        }
    }

    public OperationResult<Void> removeFromParent(
        @NotNull String path,
        @NotNull String lastBlockHash) throws PathIsNotADirectory, DirectoryNotFound, F1r3flyDeployError {
        synchronized (this) {
            String parentPath = PathUtils.getParentPath(path);
            String newChild = PathUtils.getFileName(path);


            RholangExpressionConstructor.ChannelData dir = getDir(parentPath, lastBlockHash);

            dir.children().remove(newChild);

            String rholangExpression =
                RholangExpressionConstructor.updateChildren(
                    parentPath,
                    dir.children(),
                    currentTime()
                );

            String newBlockHash = this.f1R3FlyApi.deploy(rholangExpression, false, F1r3flyApi.RHOLANG);

            return new OperationResult<>(null, newBlockHash);
        }
    }

// Private methods:

    private
    @NotNull RholangExpressionConstructor.ChannelData getDir(
        @NotNull String path,
        @NotNull String lastBlockHash) throws PathIsNotADirectory, DirectoryNotFound {

        try {
            List<RhoTypes.Par> response = this.f1R3FlyApi.getDataAtName(lastBlockHash, path);

            RholangExpressionConstructor.ChannelData data = RholangExpressionConstructor.parseChannelData(response);

            if (!data.isDir()) {
                throw new PathIsNotADirectory(path);
            }

            return data;
        } catch (NoDataByPath e) {
            throw new DirectoryNotFound(path, e);
        } catch (IllegalArgumentException e) {
            throw new PathIsNotADirectory(path, e);
        }
    }


    private
    @NotNull RholangExpressionConstructor.ChannelData getFile(
        @NotNull String path,
        @NotNull String lastBlockHash) throws PathIsNotAFile, NoDataByPath {
        try {
            List<RhoTypes.Par> response = this.f1R3FlyApi.getDataAtName(lastBlockHash, path);

            RholangExpressionConstructor.ChannelData data = RholangExpressionConstructor.parseChannelData(response);

            if (!data.isFile()) {
                throw new PathIsNotAFile(path);
            }

            return data;
        } catch (IllegalArgumentException e) {
            throw new PathIsNotAFile(path, e);
        }
    }
}
