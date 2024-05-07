package io.f1r3fly.fs.examples.storage.errors;

public class PathIsNotAFile extends F1r3flyFSError {
    public PathIsNotAFile(String path) {
        this("Path is not a file: ", path);
    }

    public PathIsNotAFile(String message, String path) {
        super(message + path);
    }

    public PathIsNotAFile(String path, Throwable cause) {
        super("Path is not a file: " + path, cause);
    }
}
