package io.f1r3fly.fs.examples.storage.inmemory.notdeployable;

import io.f1r3fly.fs.FuseFillDir;
import io.f1r3fly.fs.examples.ConfigStorage;
import io.f1r3fly.fs.examples.storage.DeployDispatcher;
import io.f1r3fly.fs.examples.storage.errors.OperationNotPermitted;
import io.f1r3fly.fs.examples.storage.grcp.F1r3flyApi;
import io.f1r3fly.fs.examples.storage.inmemory.common.IDirectory;
import io.f1r3fly.fs.examples.storage.inmemory.common.IPath;
import io.f1r3fly.fs.examples.storage.rholang.RholangExpressionConstructor;
import io.f1r3fly.fs.struct.FileStat;
import io.f1r3fly.fs.struct.FuseContext;
import jnr.ffi.Pointer;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class WalletDirectory extends AbstractNotDeployablePath implements IDirectory {
    private final String revAddress;
    private final DeployDispatcher deployDispatcher;
    private final boolean isLocked = true;

    public WalletDirectory(String prefix, String name, TokenDirectory parent, String revAddress, DeployDispatcher dispatcher) {
        super(prefix, name, parent);
        this.revAddress = revAddress;
        this.deployDispatcher = dispatcher;
    }

    @Override
    public synchronized void addChild(IPath p) throws OperationNotPermitted {
        if (p instanceof TokenFile tokenFile) {
            long amount = tokenFile.value;
            String rholang = RholangExpressionConstructor.transfer(ConfigStorage.getRevAddress(), this.revAddress, amount);

            deployDispatcher.enqueueDeploy(new DeployDispatcher.Deployment(
                rholang, false, F1r3flyApi.RHOLANG
            ));
        } else {
            throw OperationNotPermitted.instance;
        }
    }

    @Override
    public synchronized void deleteChild(IPath child) throws OperationNotPermitted {
        throw OperationNotPermitted.instance;
    }

    @Override
    public void mkdir(String lastComponent) throws OperationNotPermitted {
        throw OperationNotPermitted.instance;
    }

    @Override
    public void mkfile(String lastComponent) throws OperationNotPermitted {
        // do nothing
    }

    @Override
    public void read(Pointer buf, FuseFillDir filler) {
    }

    @Override
    public boolean isEmpty() {
        return true;
    }

    @Override
    public Set<IPath> getChildren() {
        return Set.of();
    }

    @Override
    public String getName() {
        if (isLocked) {
            return "LOCKED-REMOTE-REV-" + super.getName();
        } else {
            return super.getName();
        }
    }
}
