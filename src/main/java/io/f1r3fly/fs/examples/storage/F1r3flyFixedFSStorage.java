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

// Wraps F1r3flyFSStorage and stores block hash each "modifier" operation into a map like: `path -> block hash`
// TODO: this will be not needed if we could get any chanel using the last block hash. Currently F1r3fly API can't provide data ƒrom a chain using only last block hash
public class F1r3flyFixedFSStorage implements FSStorage {

    private static final Logger LOGGER = LoggerFactory.getLogger(F1r3flyFixedFSStorage.class.getName());

    private final F1f3flyFSStorage f1f3flyFSStorage;
    private final F1r3flyApi f1R3FlyApi;
    private final String stateChanelName;

    public F1r3flyFixedFSStorage(F1r3flyApi f1R3FlyApi, String stateChanelName) {
        this.f1f3flyFSStorage = new F1f3flyFSStorage(f1R3FlyApi);
        this.f1R3FlyApi = f1R3FlyApi;
        this.stateChanelName = stateChanelName;

    }

    @Override
    public OperationResult<TypeAndSize> getTypeAndSize(@NotNull String path, @NotNull String blockHash) throws NoDataByPath {
        Map<String, String> state = fetchState(blockHash);

        String blockHashWithPath = state.get(path);

        if (blockHashWithPath == null) {
            LOGGER.info("No data found by path {} in the state", path);
            throw new NoDataByPath(path);
        }

        return this.f1f3flyFSStorage.getTypeAndSize(path, blockHashWithPath);
    }

    @Override
    public OperationResult<Void> saveFile(String path, String content, String blockHash) throws F1r3flyDeployError, NoDataByPath, InvalidFSStructure, DirectoryNotFound {
        synchronized (this) {
            HashMap<String, String> state = fetchState(blockHash);

            String blockHashWithPath = state.get(path); // null if a new file, contains block hash if file exists

            OperationResult<Void> result = this.f1f3flyFSStorage.saveFile(path, content, blockHashWithPath);

            // update state
            state.put(path, result.blockHash());
            String newLastBlockHash = updateState(state);

            return new OperationResult<>(null, newLastBlockHash);
        }
    }

    @Override
    public OperationResult<String> readFile(String path, String blockHash) throws NoDataByPath, F1r3flyDeployError, PathIsNotAFile {
        Map<String, String> state = fetchState(blockHash);

        String blockHashWithPath = state.get(path);

        if (blockHashWithPath == null) {
            LOGGER.info("No data found by path {} in the state", path);
            throw new NoDataByPath(path);
        }

        return this.f1f3flyFSStorage.readFile(path, blockHashWithPath);
    }

    @Override
    public OperationResult<Void> deleteFile(String path, String blockHash) throws NoDataByPath, F1r3flyDeployError, PathIsNotAFile {
        synchronized (this) {
            HashMap<String, String> state = fetchState(blockHash);

            String blockHashWithPath = state.get(path);

            if (blockHashWithPath == null) {
                LOGGER.info("No data found by path {} in the state", path);
                throw new NoDataByPath(path);
            }

            OperationResult<Void> result = this.f1f3flyFSStorage.deleteFile(path, blockHashWithPath);

            // update state
            state.remove(path);
            String newLastBlockHash = updateState(state);

            return new OperationResult<>(null, newLastBlockHash);
        }
    }

    @Override
    public OperationResult<Void> createDir(String path, String blockHash) throws NoDataByPath, F1r3flyDeployError {
        synchronized (this) {
            HashMap<String, String> state = fetchState(blockHash);

            OperationResult<Void> result = this.f1f3flyFSStorage.createDir(path, blockHash);

            // update state
            state.put(path, result.blockHash());
            String newLastBlockHash = updateState(state);

            return new OperationResult<>(null, newLastBlockHash);
        }
    }

    @Override
    public OperationResult<Set<String>> readDir(String path, String blockHash) throws NoDataByPath, DirectoryNotFound, PathIsNotADirectory {
        Map<String, String> state = fetchState(blockHash);

        String blockHashWithPath = state.get(path);

        if (blockHashWithPath == null) {
            LOGGER.info("No data found by path {} in the state", path);
            throw new NoDataByPath(path);
        }

        return this.f1f3flyFSStorage.readDir(path, blockHashWithPath);
    }

    @Override
    public OperationResult<Void> deleteDir(String path, String blockHash) throws PathIsNotADirectory, DirectoryNotFound, F1r3flyDeployError, DirectoryIsNotEmpty {
        synchronized (this) {
            HashMap<String, String> state = fetchState(blockHash);

            String blockHashWithPath = state.get(path);

            if (blockHashWithPath == null) {
                LOGGER.info("No data found by path {} in the state", path);
                throw new DirectoryNotFound(path);
            }

            OperationResult<Void> result = this.f1f3flyFSStorage.deleteDir(path, blockHashWithPath);

            // update state
            state.remove(path);
            String newLastBlockHash = updateState(state);

            return new OperationResult<>(null, newLastBlockHash);
        }
    }


    @Override
    public OperationResult<Void> addToParent(String path, String blockHash) throws PathIsNotADirectory, F1r3flyDeployError, DirectoryNotFound {
        synchronized (this) {
            HashMap<String, String> state = fetchState(blockHash);
            String parentPath = path.substring(0, path.lastIndexOf('/'));

            String blockWithParent = state.get(parentPath);

            if (blockWithParent == null) {
                LOGGER.info("No data found by path {} in the state {}", parentPath, state);
                throw new DirectoryNotFound(parentPath);
            }

            OperationResult<Void> result = this.f1f3flyFSStorage.addToParent(path, blockWithParent);

            // update state
            state.put(parentPath, result.blockHash());
            String newLastBlockHash = updateState(state);

            return new OperationResult<>(null, newLastBlockHash);
        }
    }

    @Override
    public OperationResult<Void> removeFromParent(String path, String blockHash) throws PathIsNotADirectory, DirectoryNotFound, F1r3flyDeployError {
        synchronized (this) {
            HashMap<String, String> state = fetchState(blockHash);
            String parentPath = path.substring(0, path.lastIndexOf('/'));

            String blockWithParent = state.get(parentPath);

            if (blockWithParent == null) {
                LOGGER.info("No data found by path {} in the state {}", parentPath, state);
                throw new DirectoryNotFound(parentPath);
            }

            OperationResult<Void> result = this.f1f3flyFSStorage.removeFromParent(path, blockWithParent);

            // update state
            state.put(parentPath, result.blockHash());
            String newLastBlockHash = updateState(state);

            return new OperationResult<>(null, newLastBlockHash);
        }
    }

    private HashMap<String, String> fetchState(String lastBlockHash) {
        try {
            List<RhoTypes.Par> response = this.f1R3FlyApi.getDataAtName(lastBlockHash, this.stateChanelName);

            return RholangExpressionConstructor.parseEMapFromLastExpr(response);
        } catch (NoDataByPath e) {
            LOGGER.warn("Failed to fetch state from block {}", lastBlockHash, e);
            return new HashMap<>(); // empty state? lost previous state?
        }
    }

    public String initState() {
        try {
            return this.f1R3FlyApi.deploy(
                RholangExpressionConstructor.createChanelSending(
                    this.stateChanelName,
                    Map.of()
                ));

        } catch (F1r3flyDeployError e) {
            LOGGER.warn("Failed to update state", e);
            throw new RuntimeException(e); // bad situation
        }
    }

    private String updateState(HashMap<String, String> state) {
        state.put("lastUpdated", String.valueOf(System.currentTimeMillis())); // prevent "NoNewDeploys" //FIXME

        try {
            return this.f1R3FlyApi.deploy(
                RholangExpressionConstructor.createResendToChanel(
                    this.stateChanelName,
                    state
                ));

        } catch (F1r3flyDeployError e) {
            LOGGER.warn("Failed to update state", e);
            throw new RuntimeException(e); // FIXME: revert previous operation?
        }
    }
}
