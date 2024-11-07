package io.f1r3fly.fs.examples.storage.errors;

public class F1r3flyFSError extends Exception {
    public F1r3flyFSError(String message) {
        super(message);
    }

    public F1r3flyFSError(String message, Throwable cause) {
        super(message, cause);
    }
}
