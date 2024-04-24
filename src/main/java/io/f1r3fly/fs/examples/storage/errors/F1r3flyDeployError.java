package io.f1r3fly.fs.examples.storage.errors;

public class F1r3flyDeployError extends F1r3flyFSError {
    public F1r3flyDeployError(String rawRho, String message) {
        super("Failed to deploy Rholang expression: '%s'. Error: %s".formatted(rawRho, message));
    }

    public F1r3flyDeployError(String rawRho, String message, Throwable cause) {
        super("Failed to deploy Rholang expression: '%s'. Error: %s".formatted(rawRho, message), cause);
    }
}
