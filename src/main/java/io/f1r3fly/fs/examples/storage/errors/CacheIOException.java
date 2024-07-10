package io.f1r3fly.fs.examples.storage.errors;

public class CacheIOException extends F1r3flyFSError {
    public CacheIOException(String path) {
        super("Cache IO exception: " + path);
    }

    public CacheIOException(String path, Throwable cause) {
        super("Cache IO exception: " + path, cause);
    }
}
