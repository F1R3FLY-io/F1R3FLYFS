package io.f1r3fly.fs.examples.storage.errors;

public class FileAlreadyExists extends F1r3flyFSError {

    public FileAlreadyExists(String path) {
        super("File already exists: " + path);
    }

    public FileAlreadyExists(String path, Throwable cause) {
        super("File already exists: " + path, cause);
    }
} 