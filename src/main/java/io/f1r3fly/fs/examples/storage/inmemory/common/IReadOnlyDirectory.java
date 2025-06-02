package io.f1r3fly.fs.examples.storage.inmemory.common;

import io.f1r3fly.fs.examples.storage.errors.OperationNotPermitted;

public interface IReadOnlyDirectory extends IDirectory {

    @Override
    default void deleteChild(IPath child) throws OperationNotPermitted {
        throw OperationNotPermitted.instance;
    }

    @Override
    default void addChild(IPath child) throws OperationNotPermitted {
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
    default void rename(String newName, IDirectory newParent) throws OperationNotPermitted {
        throw OperationNotPermitted.instance;
    }
}
