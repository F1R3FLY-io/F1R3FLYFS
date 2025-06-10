package io.f1r3fly.f1r3drive.errors;

public class F1r3flyFSError extends RuntimeException {
    public F1r3flyFSError(String message) {
        super(message);
    }

    public F1r3flyFSError(String message, Throwable cause) {
        super(message, cause);
    }
}
