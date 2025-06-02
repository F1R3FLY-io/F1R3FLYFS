package io.f1r3fly.f1r3drive.filesystem.local;

import io.f1r3fly.f1r3drive.errors.OperationNotPermitted;
import io.f1r3fly.f1r3drive.filesystem.common.AbstractPath;
import io.f1r3fly.f1r3drive.filesystem.common.Directory;

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