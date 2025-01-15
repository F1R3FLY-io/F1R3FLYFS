package io.f1r3fly.fs.examples.storage.errors;

public class AnotherProposalInProgressError extends F1r3flyFSError {

    public AnotherProposalInProgressError(Throwable cause) {
        super("Another proposal is in progress", cause);
    }
}
