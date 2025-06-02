package io.f1r3fly.fs.examples.storage.inmemory.notdeployable;

import io.f1r3fly.fs.examples.ConfigStorage;
import io.f1r3fly.fs.examples.storage.errors.OperationNotPermitted;
import io.f1r3fly.fs.examples.storage.inmemory.common.IDirectory;
import io.f1r3fly.fs.examples.storage.inmemory.common.IFile;
import io.f1r3fly.fs.examples.storage.inmemory.common.IPath;
import io.f1r3fly.fs.examples.storage.rholang.RholangExpressionConstructor;
import io.f1r3fly.fs.struct.FileStat;
import io.f1r3fly.fs.struct.FuseContext;
import io.f1r3fly.fs.utils.PathUtils;
import jnr.ffi.Pointer;

public class TokenFile extends AbstractNotDeployablePath implements IFile {

    final long value;

    public TokenFile(String name, TokenDirectory parent, long value) {
        super(name, parent);
        this.value = value;
    }

    @Override
    public int read(Pointer buffer, long size, long offset)  { return 0; }
    @Override
    public int write(Pointer buffer, long bufSize, long writeOffset) { return 0;}
    @Override
    public void truncate(long offset) {}
    @Override
    public void open() {}
    @Override
    public void close() {}

    @Override
    public void rename(String newName, IDirectory newParent) throws OperationNotPermitted {
        if (!this.name.equals(newName)) {
            System.out.println("Renaming " + name + " to " + newName);
            throw OperationNotPermitted.instance;
        }

        // allow to move TokenFile inside WalletDirectory only
        if (!(newParent instanceof WalletDirectory)) {
            throw OperationNotPermitted.instance;
        }

        this.parent = newParent;
    }
}
