package io.f1r3fly.fs.examples.storage.errors;

public class FileNotFoundError extends F1r3flyFSError {
    public FileNotFoundError(String path) {
        super("File not found: " + path);
    }

    public FileNotFoundError(String path, Throwable cause) {
        super("File not found: " + path, cause);
    }
}
