package io.f1r3fly.fs.examples.storage.filesystem.local;

import io.f1r3fly.fs.examples.storage.errors.OperationNotPermitted;
import io.f1r3fly.fs.examples.storage.filesystem.common.AbstractPath;
import io.f1r3fly.fs.examples.storage.filesystem.common.Directory;

public abstract class AbstractLocalPath extends AbstractPath {

    public AbstractLocalPath(String name, Directory parent) {
        super(name, parent);
    }

    @Override
    public void delete() throws OperationNotPermitted {
        throw OperationNotPermitted.instance;
    }

    @Override
    public void rename(String newName, Directory newParent) throws OperationNotPermitted {
        throw OperationNotPermitted.instance;
    }
} 