package io.f1r3fly.fs.examples.storage.errors;

public class PathNotFound extends F1r3flyFSError {
    public PathNotFound(String path) {
        super("Path not found: " + path);
    }

    public PathNotFound(String path, Throwable cause) {
        super("Path not found: " + path, cause);
    }
}
