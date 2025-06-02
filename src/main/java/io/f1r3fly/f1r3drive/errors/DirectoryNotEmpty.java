package io.f1r3fly.f1r3drive.errors;

public class DirectoryNotEmpty extends F1r3flyFSError {

    public DirectoryNotEmpty(String path) {
        super("Directory not empty: " + path);
    }

    public DirectoryNotEmpty(String path, Throwable cause) {
        super("Directory not empty: " + path, cause);
    }
} 