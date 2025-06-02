package io.f1r3fly.f1r3drive.filesystem.local;

import io.f1r3fly.f1r3drive.errors.OperationNotPermitted;
import io.f1r3fly.f1r3drive.filesystem.common.Directory;
import io.f1r3fly.f1r3drive.filesystem.common.File;
import io.f1r3fly.f1r3drive.filesystem.deployable.UnlockedWalletDirectory;
import jnr.ffi.Pointer;

public class TokenFile extends AbstractLocalPath implements File {

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
    public void rename(String newName, Directory newParent) throws OperationNotPermitted {
        if (!this.name.equals(newName)) {
            System.out.println("Renaming " + name + " to " + newName);
            throw OperationNotPermitted.instance;
        }

        // allow to move TokenFile inside Wallet Directory only
        if (!(newParent instanceof LockedRemoteDirectory) && !(newParent instanceof UnlockedWalletDirectory)) {
            throw OperationNotPermitted.instance;
        }

        this.parent = newParent;
    }
}
