package io.f1r3fly.fs.examples.storage.errors;

public class OutOfDeployRetriesError extends F1r3flyDeployError {
    public OutOfDeployRetriesError(String deployId) {
        super(deployId, "Out of retries");
    }
}
