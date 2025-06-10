package io.f1r3fly.f1r3drive.errors;

public class NoDataByPath extends F1r3DriveError {
    public NoDataByPath(String path) {
        super("No data found by path %s".formatted(path));
    }

    public NoDataByPath(String path, String blockHash) {
        super("Failed to get data by path %s and block bash %s".formatted(path, blockHash));
    }

    public NoDataByPath(String path, String blockHash, Throwable cause) {
        super("Failed to get data by path %s and block bash %s".formatted(path, blockHash), cause);
    }

    public NoDataByPath(String path, Throwable cause) {
        super("Failed to get data by path %s".formatted(path), cause);
    }
}
