package io.f1r3fly.fs.examples.storage.errors;

public class PathIsNotADirectory extends F1r3flyFSError {
    public PathIsNotADirectory(String path) {
        super("Path is not a directory: " + path);
    }

    public PathIsNotADirectory(String path, Throwable cause) {
        super("Path is not a directory: " + path, cause);
    }
}
