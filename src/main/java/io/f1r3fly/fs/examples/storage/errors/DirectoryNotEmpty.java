package io.f1r3fly.fs.examples.storage.errors;

public class DirectoryNotEmpty extends F1r3flyFSError {

    public DirectoryNotEmpty(String path) {
        super("Directory not empty: " + path);
    }

    public DirectoryNotEmpty(String path, Throwable cause) {
        super("Directory not empty: " + path, cause);
    }
} 