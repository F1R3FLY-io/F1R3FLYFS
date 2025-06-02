package io.f1r3fly.f1r3drive.errors;

public class OperationNotPermitted extends F1r3flyFSError {

    public static OperationNotPermitted instance = new OperationNotPermitted();

    private OperationNotPermitted() {
        super("Operation not permitted");
    }


}
