package io.f1r3fly.fs.examples.storage.errors;

public class OperationNotPermitted extends F1r3flyFSError {

    public static OperationNotPermitted instance = new OperationNotPermitted();

    private OperationNotPermitted() {
        super("Operation not permitted");
    }


}
