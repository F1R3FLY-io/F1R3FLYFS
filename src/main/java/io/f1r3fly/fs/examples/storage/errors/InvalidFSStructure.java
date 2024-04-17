package io.f1r3fly.fs.examples.storage.errors;

public class InvalidFSStructure extends F1r3flyFSError {
    public InvalidFSStructure(String message) {
        super(message);
    }

    public InvalidFSStructure(String message, Throwable cause) {
        super(message, cause);
    }
}
