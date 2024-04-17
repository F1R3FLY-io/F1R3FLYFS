package io.f1r3fly.fs.examples.storage.errors;

public class F1r3flyDeployError extends F1r3flyFSError {
    public F1r3flyDeployError(String message) {
        super(message);
    }

    public F1r3flyDeployError(String message, Throwable cause) {
        super(message, cause);
    }
}
