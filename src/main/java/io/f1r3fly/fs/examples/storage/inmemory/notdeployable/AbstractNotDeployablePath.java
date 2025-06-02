package io.f1r3fly.fs.examples.storage.inmemory.notdeployable;

import io.f1r3fly.fs.examples.storage.errors.OperationNotPermitted;
import io.f1r3fly.fs.examples.storage.inmemory.common.AbstractPath;
import io.f1r3fly.fs.examples.storage.inmemory.common.IDirectory;
import io.f1r3fly.fs.struct.FileStat;
import io.f1r3fly.fs.struct.FuseContext;

public abstract class AbstractNotDeployablePath extends AbstractPath {

    public AbstractNotDeployablePath(String name, IDirectory parent) {
        super(name, parent);
    }

    @Override
    public void delete() throws OperationNotPermitted {
        throw OperationNotPermitted.instance;
    }

    @Override
    public void rename(String newName, IDirectory newParent) throws OperationNotPermitted {
        throw OperationNotPermitted.instance;
    }
} 