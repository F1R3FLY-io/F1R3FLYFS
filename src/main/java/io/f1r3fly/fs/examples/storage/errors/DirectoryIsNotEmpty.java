package io.f1r3fly.fs.examples.storage.errors;

public class DirectoryIsNotEmpty extends F1r3flyFSError {
    public DirectoryIsNotEmpty(String path) {
        super("Directory is not empty: " + path);
    }

    public DirectoryIsNotEmpty(String path, Throwable cause) {
        super("Directory is not empty: " + path, cause);
    }
}
