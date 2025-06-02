package io.f1r3fly.fs.examples.storage.filesystem.common;

import io.f1r3fly.fs.examples.storage.errors.OperationNotPermitted;

public interface ReadOnlyDirectory extends Directory {

    @Override
    default void deleteChild(Path child) throws OperationNotPermitted {
        throw OperationNotPermitted.instance;
    }

    @Override
    default void addChild(Path child) throws OperationNotPermitted {
        throw OperationNotPermitted.instance;
    }

    @Override
    default void mkdir(String lastComponent) throws OperationNotPermitted {
        throw OperationNotPermitted.instance;
    }

    @Override
    default void mkfile(String lastComponent) throws OperationNotPermitted {
        throw OperationNotPermitted.instance;
    }

    @Override
    default void delete() throws OperationNotPermitted {
        throw OperationNotPermitted.instance;
    }

    @Override
    default void rename(String newName, Directory newParent) throws OperationNotPermitted {
        throw OperationNotPermitted.instance;
    }
}
