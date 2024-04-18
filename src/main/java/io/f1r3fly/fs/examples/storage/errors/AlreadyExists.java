package io.f1r3fly.fs.examples.storage.errors;

public class AlreadyExists extends F1r3flyFSError {
    public AlreadyExists(String path) {
        super("Already exists: " + path);
    }

    public AlreadyExists(String path, Throwable cause) {
        super("Already exists: " + path, cause);
    }
}
