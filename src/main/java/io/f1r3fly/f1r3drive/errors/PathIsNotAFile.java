package io.f1r3fly.f1r3drive.errors;

public class PathIsNotAFile extends F1r3DriveError {
    public PathIsNotAFile(String path) {
        super("Path is not a file: " + path);
    }

    public PathIsNotAFile(String path, Throwable cause) {
        super("Path is not a file: " + path, cause);
    }
}
