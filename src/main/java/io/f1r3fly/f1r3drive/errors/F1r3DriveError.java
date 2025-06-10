package io.f1r3fly.f1r3drive.errors;

public class F1r3DriveError extends RuntimeException {
    public F1r3DriveError(String message) {
        super(message);
    }

    public F1r3DriveError(String message, Throwable cause) {
        super(message, cause);
    }
}
