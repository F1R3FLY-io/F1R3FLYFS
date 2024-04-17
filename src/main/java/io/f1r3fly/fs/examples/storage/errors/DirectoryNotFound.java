package io.f1r3fly.fs.examples.storage.errors;

public class DirectoryNotFound extends F1r3flyFSError {
    public DirectoryNotFound(String path) {
        super("Directory not found: " + path);
    }

    public DirectoryNotFound(String path, Throwable cause) {
        super("Directory not found: " + path, cause);
    }
}
