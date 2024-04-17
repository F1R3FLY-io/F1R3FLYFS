package io.f1r3fly.fs.examples.storage;

import io.f1r3fly.fs.examples.storage.errors.*;
import io.f1r3fly.fs.examples.storage.grcp.F1r3flyApi;
import io.f1r3fly.fs.examples.storage.rholang.RholangExpressionConstructor;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rhoapi.RhoTypes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class F1f3flyFSStorage implements FSStorage {

    private final F1r3flyApi f1R3FlyApi;

    private final Logger LOGGER = LoggerFactory.getLogger(this.getClass().getName());

    private static final String TYPE = "type";
    public static final String DIR_TYPE = "d";
    public static final String FILE_TYPE = "f";

    private static final String VALUE = "value";

    private static final char PATH_DELIMITER = '/';

    public F1f3flyFSStorage(F1r3flyApi f1R3FlyApi) {
        this.f1R3FlyApi = f1R3FlyApi;
    }

    @Override
    public OperationResult<TypeAndSize> getTypeAndSize(@NotNull String path, @NotNull String lastBlockHash) throws NoDataByPath {
        // gets data from `@"path/to/something"` channel
        List<RhoTypes.Par> response = this.f1R3FlyApi.getDataAtName(lastBlockHash, path);

        HashMap<String, String> parsed = RholangExpressionConstructor.parseEMapFromLastExpr(response);

        String fileOrDirType = parsed.get(TYPE);
        int size = 0;
        if (fileOrDirType.equals(FILE_TYPE)) {
            size = parsed.get(VALUE).length();
        }

        return new OperationResult<>(new TypeAndSize(fileOrDirType, size), lastBlockHash); // block hash is the same because of no changes
    }

    @Override
    public OperationResult<Void> saveFile(String path, String content, String lastBlockHash) throws F1r3flyDeployError, NoDataByPath, InvalidFSStructure, DirectoryNotFound {
        // TODO remove lastBlockHash, no need to pass it here

        synchronized (this) {
            // Rholang looks like `@"path/to/something"!({"type": "f", "value": "content"})`
            String rholangExpression =
                RholangExpressionConstructor.createChanelSending(
                    path,
                    Map.of(
                        TYPE, FILE_TYPE, // add 'mode' field here later
                        VALUE, content)
                );

            String newBlockHash = this.f1R3FlyApi.deploy(rholangExpression);

            return new OperationResult<>(null, newBlockHash);
        }
    }

    @Override
    public OperationResult<String> readFile(String path, String lastBlockHash) throws NoDataByPath, PathIsNotAFile {

        // reads data from `@"path/to/something"` channel
        Map<String, String> chanelContent = getFile(path, lastBlockHash);

        return new OperationResult<>(chanelContent.get(VALUE), lastBlockHash); // block hash is the same because of no changes
    }

    private @NotNull Map<String, String> getFile(String path, String lastBlockHash) throws PathIsNotAFile, NoDataByPath {

        List<RhoTypes.Par> response = this.f1R3FlyApi.getDataAtName(lastBlockHash, path);

        HashMap<String, String> chanelContent = RholangExpressionConstructor.parseEMapFromLastExpr(response);

        // if not a file
        if (!chanelContent.getOrDefault(TYPE, "").equals(FILE_TYPE)) {
            throw new PathIsNotAFile(path);
        }
        return chanelContent;
    }


    @Override
    public OperationResult<Void> deleteFile(String path, String lastBlockHash) throws NoDataByPath, F1r3flyDeployError, PathIsNotAFile {
        synchronized (this) {
            getFile(path, lastBlockHash); // check if file getType

            String newBlockHash =
                this.f1R3FlyApi.deploy(RholangExpressionConstructor.createConsumeFromChanel(path)); // forges a data

            return new OperationResult<>(null, newBlockHash);
        }
    }

    @Override
    public OperationResult<Void> createDir(String path, String blockHash) throws F1r3flyDeployError {
        // TODO blockhash is not used here, remove

        synchronized (this) {
            // Rholang looks like `@"path/to/something"!({"type": "d", "value": "[]"})`
            String rholangExpression =
                RholangExpressionConstructor.createChanelSending(
                    path,
                    Map.of(
                        TYPE, DIR_TYPE, // add 'mode' field here later
                        VALUE, RholangExpressionConstructor.EmptyList)
                );

            String newBlockHash = this.f1R3FlyApi.deploy(rholangExpression);

            return new OperationResult<>(null, newBlockHash);
        }
    }

    @Override
    public OperationResult<Set<String>> readDir(String path, String lastBlockHash) throws DirectoryNotFound, NoDataByPath, PathIsNotADirectory {
        // reads data from `@"path/to/something"` channel
        Map<String, String> chanelContent = getDir(path, lastBlockHash);

        String children = chanelContent.get(VALUE);

        // "[\"child1\", \"child2\", ...]" => ["child1", "child2", ...]
        Set<String> parsed = RholangExpressionConstructor.parseList(children);

        return new OperationResult<>(parsed, lastBlockHash); // block hash is the same because of no changes
    }

    @Override
    public OperationResult<Void> deleteDir(String path, String lastBlockHash) throws PathIsNotADirectory, DirectoryNotFound, F1r3flyDeployError, DirectoryIsNotEmpty {
        synchronized (this) {
            Map<String, String> dir = getDir(path, lastBlockHash);

            Set<String> children = RholangExpressionConstructor.parseList(dir.get(VALUE));

            if (!children.isEmpty()) {
                throw new DirectoryIsNotEmpty(path);
            }

            String newBlockHash =
                this.f1R3FlyApi.deploy(RholangExpressionConstructor.createConsumeFromChanel(path)); // forgets a data

            return new OperationResult<>(null, newBlockHash);
        }
    }


    public OperationResult<Void> addToParent(String path, String lastBlockHash) throws PathIsNotADirectory, F1r3flyDeployError, DirectoryNotFound {

        synchronized (this) {

            String parentPath = path.substring(0, path.lastIndexOf(PATH_DELIMITER));
            String newChild = path.substring(path.lastIndexOf(PATH_DELIMITER) + 1);

            Map<String, String> dir = getDir(parentPath, lastBlockHash);

            Set<String> children = RholangExpressionConstructor.parseList(dir.get(VALUE));
            children.add(newChild);

            // Gets: {"type": "d", "value": "[\"child1\", \"child2\"]"}
            // Sets: {"type": "d", "value": "[\"child1\", \"child2\", \"newChild\"]"})`
            String rholangExpression =
                RholangExpressionConstructor.createResendToChanel(
                    parentPath,
                    Map.of(
                        TYPE, DIR_TYPE, // add 'mode' field here later
                        VALUE, RholangExpressionConstructor.set2String(children)
                    )
                );

            String newBlockHash = this.f1R3FlyApi.deploy(rholangExpression);

            return new OperationResult<>(null, newBlockHash);
        }
    }

    public OperationResult<Void> removeFromParent(String path, String lastBlockHash) throws PathIsNotADirectory, DirectoryNotFound, F1r3flyDeployError {
        synchronized (this) {
            String parentPath = path.substring(0, path.lastIndexOf(PATH_DELIMITER));
            String newChild = path.substring(path.lastIndexOf(PATH_DELIMITER) + 1);


            Map<String, String> dir = getDir(parentPath, lastBlockHash);

            Set<String> children = RholangExpressionConstructor.parseList(dir.get(VALUE));
            children.remove(newChild);

            // Gets: {"type": "d", "value": "[\"child1\", \"child2\"]"}
            // Sets: {"type": "d", "value": "[\"child1\"]"})`
            String rholangExpression =
                RholangExpressionConstructor.createResendToChanel(
                    parentPath,
                    Map.of(
                        TYPE, DIR_TYPE, // add 'mode' field here later
                        VALUE, RholangExpressionConstructor.set2String(children)
                    )
                );

            String newBlockHash = this.f1R3FlyApi.deploy(rholangExpression);

            return new OperationResult<>(null, newBlockHash);
        }
    }

// Private methods:

    private @NotNull Map<String, String> getDir(String path, String lastBlockHash) throws PathIsNotADirectory, DirectoryNotFound {

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

}
