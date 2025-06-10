package io.f1r3fly.f1r3drive.errors;

public class InvalidSigningKeyException extends F1r3DriveError {
    public InvalidSigningKeyException(String message) {
        super(message);
    }

    public InvalidSigningKeyException(String message, Throwable cause) {
        super(message, cause);
    }
} 