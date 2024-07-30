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

public class F1f3flyFSStorage {

    private final F1r3flyApi f1R3FlyApi;

    public F1f3flyFSStorage(F1r3flyApi f1R3FlyApi) {
        this.f1R3FlyApi = f1R3FlyApi;
    }

    public RholangExpressionConstructor.ChannelData getTypeAndSize(
        @NotNull String path) throws NoDataByPath {
        // gets data from `@"path/to/something"` channel
        List<RhoTypes.Par> response = this.f1R3FlyApi.findDataByName(path);

        try {
            RholangExpressionConstructor.ChannelData parsed = RholangExpressionConstructor.parseChannelData(response);

            return parsed;
        } catch (IllegalArgumentException e) {
            throw new NoDataByPath(path, e);
        }
    }

    public void rename(
        @NotNull String oldPath,
        @NotNull String newPath) throws NoDataByPath, F1r3flyDeployError, DirectoryNotFound, PathIsNotADirectory, DirectoryIsNotEmpty, AlreadyExists {

        synchronized (this) {
            // check if exists (fails if not found by old path)
            RholangExpressionConstructor.ChannelData fileOrDir = getTypeAndSize(oldPath);

            try {
                // check if exists (fails if found by new path)
                getTypeAndSize(newPath);
                throw new AlreadyExists(newPath);
            } catch (NoDataByPath e) {
                // ok
            }

            if (fileOrDir.isDir() && !fileOrDir.children().isEmpty()) {
                throw new DirectoryIsNotEmpty(oldPath);
            }

            this.f1R3FlyApi.deploy(
                RholangExpressionConstructor.renameChanel(oldPath, newPath),
                true,
                F1r3flyApi.RHOLANG);
        }

    }

    public String createFile(
        @NotNull String path,
        @NotNull byte[] content,
        long size) throws F1r3flyDeployError {

        synchronized (this) {
            String rholangExpression =
                RholangExpressionConstructor.sendFileIntoNewChanel(
                    path,
                    size,
                    content,
                    currentTime()
                );

            return this.f1R3FlyApi.deploy(rholangExpression, false, F1r3flyApi.RHOLANG);
        }
    }


    public void appendFile(
        @NotNull String path,
        @NotNull byte[] content,
        long size) throws F1r3flyDeployError {

        synchronized (this) {
            String rholangExpression =
                RholangExpressionConstructor.appendValue(
                    path,
                    currentTime(),
                    content,
                    size
                );

            this.f1R3FlyApi.deploy(rholangExpression, true, F1r3flyApi.RHOLANG);
        }
    }

    private static @NotNull long currentTime() {
        return System.currentTimeMillis();
    }

    public byte[] readFile(
        @NotNull String path) throws NoDataByPath, PathIsNotAFile {

        // reads data from `@"path/to/something"` channel
        RholangExpressionConstructor.ChannelData chanelContent = getFile(path);

        return chanelContent.fileContent();
    }

    public String deployFile(
        @NotNull String path) throws NoDataByPath, F1r3flyDeployError, PathIsNotAFile {
        if (!PathUtils.isDeployableFile(path)) {
            throw new PathIsNotAFile("The file is not Rho or Metta type", path);
        }

        synchronized (this) {
            byte[] fileContent = readFile(path);
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

            createFile(
                fullPathWithBlockHashFile,
                encodedFileContent,
                blockWithExecutedRholang.length());

            return fullPathWithBlockHashFile;
        }

    }

    public void deleteFile(
        @NotNull String path) throws NoDataByPath, F1r3flyDeployError, PathIsNotAFile {
        synchronized (this) {
            getFile(path); // check if file getType

            this.f1R3FlyApi.deploy(
                RholangExpressionConstructor.readAndForget(path, currentTime()),
                false,
                F1r3flyApi.RHOLANG); // forges a data
        }
    }

    public void createDir(
        @NotNull String path) throws F1r3flyDeployError {

        synchronized (this) {
            // Rholang looks like `@"path/to/something"!({"type": "d", "children": "[]", "lastUpdated": 1234567890})`
            String rholangExpression =
                RholangExpressionConstructor.sendDirectoryIntoNewChannel(
                    path,
                    new HashSet<>(), // empty set of children
                    currentTime()
                );

            this.f1R3FlyApi.deploy(rholangExpression, false, F1r3flyApi.RHOLANG);
        }
    }

    public Set<String> readDir(
        @NotNull String path) throws DirectoryNotFound, NoDataByPath, PathIsNotADirectory {
        // reads data from `@"path/to/something"` channel
        RholangExpressionConstructor.ChannelData channelContent = getDir(path);


        return channelContent.children(); // block hash is the same because of no changes
    }

    public void deleteDir(
        @NotNull String path) throws PathIsNotADirectory, DirectoryNotFound, F1r3flyDeployError, DirectoryIsNotEmpty {
        synchronized (this) {
            RholangExpressionConstructor.ChannelData dir = getDir(path);

            if (!dir.children().isEmpty()) {
                throw new DirectoryIsNotEmpty(path);
            }


            this.f1R3FlyApi.deploy(
                RholangExpressionConstructor.readAndForget(path, currentTime()),
                false,
                F1r3flyApi.RHOLANG); // forgets a data

        }
    }


    public void addToParent(
        @NotNull String path) throws PathIsNotADirectory, F1r3flyDeployError, DirectoryNotFound {

        synchronized (this) {

            String parentPath = PathUtils.getParentPath(path);
            String newChild = PathUtils.getFileName(path);

            RholangExpressionConstructor.ChannelData dir = getDir(parentPath);

            dir.children().add(newChild);

            String rholangExpression =
                RholangExpressionConstructor.updateChildren(
                    parentPath,
                    dir.children(),
                    currentTime()
                );

            this.f1R3FlyApi.deploy(rholangExpression, false, F1r3flyApi.RHOLANG);
        }
    }

    public void removeFromParent(
        @NotNull String path) throws PathIsNotADirectory, DirectoryNotFound, F1r3flyDeployError {
        synchronized (this) {
            String parentPath = PathUtils.getParentPath(path);
            String newChild = PathUtils.getFileName(path);


            RholangExpressionConstructor.ChannelData dir = getDir(parentPath);

            dir.children().remove(newChild);

            String rholangExpression =
                RholangExpressionConstructor.updateChildren(
                    parentPath,
                    dir.children(),
                    currentTime()
                );

            this.f1R3FlyApi.deploy(rholangExpression, false, F1r3flyApi.RHOLANG);
        }
    }

// Private methods:

    private
    @NotNull RholangExpressionConstructor.ChannelData getDir(
        @NotNull String path) throws PathIsNotADirectory, DirectoryNotFound {

        try {
            List<RhoTypes.Par> response = this.f1R3FlyApi.findDataByName(path);

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
        @NotNull String path) throws PathIsNotAFile, NoDataByPath {
        try {
            List<RhoTypes.Par> response = this.f1R3FlyApi.findDataByName(path);

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
