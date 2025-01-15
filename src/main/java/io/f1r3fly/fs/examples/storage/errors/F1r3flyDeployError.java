package io.f1r3fly.fs.examples.storage.errors;

public class F1r3flyDeployError extends F1r3flyFSError {
    public F1r3flyDeployError(String deployId, String message) {
        super("Failed to deploy (ID '%s'). Error: %s".formatted(deployId, message));
    }

    public F1r3flyDeployError(String deployId, String message, Throwable cause) {
        super("Failed to deploy (ID: '%s'). Error: %s".formatted(deployId, message), cause);
    }
}
