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

    private static final String TYPE = "type";
    public static final String DIR_TYPE = "d";
    public static final String FILE_TYPE = "f";

    private static final String VALUE = "value";
    private static final String LAST_UPDATED = "lastUpdated";

    public F1f3flyFSStorage(F1r3flyApi f1R3FlyApi) {
        this.f1R3FlyApi = f1R3FlyApi;
    }

    @Override
    public OperationResult<TypeAndSize> getTypeAndSize(
        @NotNull String path,
        @NotNull String lastBlockHash) throws NoDataByPath {
        // gets data from `@"path/to/something"` channel
        List<RhoTypes.Par> response = this.f1R3FlyApi.getDataAtName(lastBlockHash, path);

        HashMap<String, String> parsed = RholangExpressionConstructor.parseEMapFromLastExpr(response);

        String fileOrDirType = parsed.get(TYPE);
        int size = -1;
        if (fileOrDirType.equals(FILE_TYPE)) {
            try {
                size = Base64.getDecoder().decode(parsed.get(VALUE)).length; // TODO: don't get data back for 'size' field
            } catch (IllegalArgumentException e) {
                LOGGER.error("Failed to decode base64 'value' field for path: {}", path, e);
            }
        }

        // block hash is the same because of no changes
        return new OperationResult<>(new TypeAndSize(fileOrDirType, size), lastBlockHash);
    }

    @Override
    public OperationResult<Void> rename(
        @NotNull String oldPath,
        @NotNull String newPath,
        @NotNull String blockHash) throws NoDataByPath, F1r3flyDeployError, DirectoryNotFound, PathIsNotADirectory, DirectoryIsNotEmpty, AlreadyExists {

        synchronized (this) {
            // check if exists (fails if not found by old path)
            String fileOrDir = getTypeAndSize(oldPath, blockHash)
                .payload()
                .type();

            try {
                // check if exists (fails if found by new path)
                getTypeAndSize(newPath, blockHash);
                throw new AlreadyExists(newPath);
            } catch (NoDataByPath e) {
                // ok
            }

            if (fileOrDir.equals(DIR_TYPE)) {
                Map<String, String> dir = getDir(oldPath, blockHash);
                Set<String> children = RholangExpressionConstructor.parseList(dir.get(VALUE));
                if (!children.isEmpty()) {
                    throw new DirectoryIsNotEmpty(oldPath);
                }
            }

            String blockHashAfterRename = this.f1R3FlyApi.deploy(RholangExpressionConstructor.renameChanel(oldPath, newPath));

            // block hash is the same because of no changes
            return new OperationResult<>(null, blockHashAfterRename);
        }

    }

    @Override
    public OperationResult<Void> createFile(
        @NotNull String path,
        @NotNull String content,
        @NotNull String blockHash) throws F1r3flyDeployError {
        // TODO remove lastBlockHash, no need to pass it here

        synchronized (this) {
            String rholangExpression =
                RholangExpressionConstructor.sendValueIntoNewChanel(
                    path,
                    Map.of(
                        TYPE, FILE_TYPE, // add 'mode' field here later
                        LAST_UPDATED, currentTime(),
                        VALUE, content)
                );

            String newBlockHash = this.f1R3FlyApi.deploy(rholangExpression);

            return new OperationResult<>(null, newBlockHash);
        }
    }


    @Override
    public OperationResult<Void> appendFile(
        @NotNull String path,
        @NotNull String content,
        @NotNull String lastBlockHash) throws F1r3flyDeployError {
        // TODO remove lastBlockHash, no need to pass it here

        synchronized (this) {
            String rholangExpression =
                RholangExpressionConstructor.appendValue(
                    path,
                    currentTime(),
                    content
                );

            String newBlockHash = this.f1R3FlyApi.deploy(rholangExpression);

            return new OperationResult<>(null, newBlockHash);
        }
    }

    private static @NotNull String currentTime() {
        return String.valueOf(System.currentTimeMillis());
    }

    @Override
    public OperationResult<String> readFile(
        @NotNull String path,
        @NotNull String lastBlockHash) throws NoDataByPath, PathIsNotAFile {

        // reads data from `@"path/to/something"` channel
        Map<String, String> chanelContent = getFile(path, lastBlockHash);

        return new OperationResult<>(chanelContent.get(VALUE), lastBlockHash); // block hash is the same because of no changes
    }


    @Override
    public OperationResult<Void> deleteFile(
        @NotNull String path,
        @NotNull String lastBlockHash) throws NoDataByPath, F1r3flyDeployError, PathIsNotAFile {
        synchronized (this) {
            getFile(path, lastBlockHash); // check if file getType

            String newBlockHash =
                this.f1R3FlyApi.deploy(RholangExpressionConstructor.readAndForget(path, currentTime())); // forges a data

            return new OperationResult<>(null, newBlockHash);
        }
    }

    @Override
    public OperationResult<Void> createDir(
        @NotNull String path,
        @NotNull String blockHash) throws F1r3flyDeployError {
        // TODO blockhash is not used here, remove

        synchronized (this) {
            // Rholang looks like `@"path/to/something"!({"type": "d", "value": "[]"})`
            String rholangExpression =
                RholangExpressionConstructor.sendValueIntoNewChanel(
                    path,
                    Map.of(
                        TYPE, DIR_TYPE, // add 'mode' field here later
                        LAST_UPDATED, currentTime(),
                        VALUE, RholangExpressionConstructor.EmptyList)
                );

            String newBlockHash = this.f1R3FlyApi.deploy(rholangExpression);

            return new OperationResult<>(null, newBlockHash);
        }
    }

    @Override
    public OperationResult<Set<String>> readDir(
        @NotNull String path,
        @NotNull String lastBlockHash) throws DirectoryNotFound, NoDataByPath, PathIsNotADirectory {
        // reads data from `@"path/to/something"` channel
        Map<String, String> chanelContent = getDir(path, lastBlockHash);

        String children = chanelContent.get(VALUE);

        // "[\"child1\", \"child2\", ...]" => ["child1", "child2", ...]
        Set<String> parsed = RholangExpressionConstructor.parseList(children);

        return new OperationResult<>(parsed, lastBlockHash); // block hash is the same because of no changes
    }

    @Override
    public OperationResult<Void> deleteDir(
        @NotNull String path,
        @NotNull String lastBlockHash) throws PathIsNotADirectory, DirectoryNotFound, F1r3flyDeployError, DirectoryIsNotEmpty {
        synchronized (this) {
            Map<String, String> dir = getDir(path, lastBlockHash);

            Set<String> children = RholangExpressionConstructor.parseList(dir.get(VALUE));

            if (!children.isEmpty()) {
                throw new DirectoryIsNotEmpty(path);
            }

            String newBlockHash =
                this.f1R3FlyApi.deploy(RholangExpressionConstructor.readAndForget(path, currentTime())); // forgets a data

            return new OperationResult<>(null, newBlockHash);
        }
    }


    public OperationResult<Void> addToParent(
        @NotNull String path,
        @NotNull String lastBlockHash) throws PathIsNotADirectory, F1r3flyDeployError, DirectoryNotFound {

        synchronized (this) {

            String parentPath = PathUtils.getParentPath(path);
            String newChild = PathUtils.getFileName(path);

            Map<String, String> dir = getDir(parentPath, lastBlockHash);

            Set<String> children = RholangExpressionConstructor.parseList(dir.get(VALUE));
            children.add(newChild);

            // Gets: {"type": "d", "value": "[\"child1\", \"child2\"]"}
            // Sets: {"type": "d", "value": "[\"child1\", \"child2\", \"newChild\"]"})`
            String rholangExpression =
                RholangExpressionConstructor.replaceValue(
                    parentPath,
                    Map.of(
                        TYPE, DIR_TYPE, // add 'mode' field here later
                        LAST_UPDATED, currentTime(),
                        VALUE, RholangExpressionConstructor.set2String(children)
                    )
                );

            String newBlockHash = this.f1R3FlyApi.deploy(rholangExpression);

            return new OperationResult<>(null, newBlockHash);
        }
    }

    public OperationResult<Void> removeFromParent(
        @NotNull String path,
        @NotNull String lastBlockHash) throws PathIsNotADirectory, DirectoryNotFound, F1r3flyDeployError {
        synchronized (this) {
            String parentPath = PathUtils.getParentPath(path);
            String newChild = PathUtils.getFileName(path);


            Map<String, String> dir = getDir(parentPath, lastBlockHash);

            Set<String> children = RholangExpressionConstructor.parseList(dir.get(VALUE));
            children.remove(newChild);

            // Gets: {"type": "d", "value": "[\"child1\", \"child2\"]"}
            // Sets: {"type": "d", "value": "[\"child1\"]"})`
            String rholangExpression =
                RholangExpressionConstructor.replaceValue(
                    parentPath,
                    Map.of(
                        TYPE, DIR_TYPE, // add 'mode' field here later
                        LAST_UPDATED, currentTime(),
                        VALUE, RholangExpressionConstructor.set2String(children)
                    )
                );

            String newBlockHash = this.f1R3FlyApi.deploy(rholangExpression);

            return new OperationResult<>(null, newBlockHash);
        }
    }

// Private methods:

    private
    @NotNull Map<String, String> getDir(
        @NotNull String path,
        @NotNull String lastBlockHash) throws PathIsNotADirectory, DirectoryNotFound {

        try {
            List<RhoTypes.Par> response = this.f1R3FlyApi.getDataAtName(lastBlockHash, path);

            HashMap<String, String> chanelContent = RholangExpressionConstructor.parseEMapFromLastExpr(response);

            // if not a dir
            if (!chanelContent.getOrDefault(TYPE, "").equals(DIR_TYPE)) {
                throw new PathIsNotADirectory("Path is not a dir");
            }
            return chanelContent;
        } catch (NoDataByPath e) {
            throw new DirectoryNotFound(path, e);
        }
    }


    private
    @NotNull Map<String, String> getFile(
        @NotNull String path,
        @NotNull String lastBlockHash) throws PathIsNotAFile, NoDataByPath {

        List<RhoTypes.Par> response = this.f1R3FlyApi.getDataAtName(lastBlockHash, path);

        HashMap<String, String> chanelContent = RholangExpressionConstructor.parseEMapFromLastExpr(response);

        // if not a file
        if (!chanelContent.getOrDefault(TYPE, "").equals(FILE_TYPE)) {
            throw new PathIsNotAFile(path);
        }
        return chanelContent;
    }

}
