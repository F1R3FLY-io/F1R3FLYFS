package io.f1r3fly.fs.examples.storage.inmemory.notdeployable;

import io.f1r3fly.fs.FuseFillDir;
import io.f1r3fly.fs.examples.ConfigStorage;
import io.f1r3fly.fs.examples.storage.DeployDispatcher;
import io.f1r3fly.fs.examples.storage.errors.OperationNotPermitted;
import io.f1r3fly.fs.examples.storage.grcp.F1r3flyApi;
import io.f1r3fly.fs.examples.storage.inmemory.common.IDirectory;
import io.f1r3fly.fs.examples.storage.inmemory.common.IPath;
import io.f1r3fly.fs.examples.storage.rholang.RholangExpressionConstructor;
import jnr.ffi.Pointer;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public class WalletDirectory extends AbstractNotDeployablePath implements IDirectory {
    private final String revAddress;
    private final DeployDispatcher deployDispatcher;
    private final boolean isLocked = true;
    private final byte[] signingKey;


    // TODO: add a way to get the signing key from the file system
    public WalletDirectory(String name, IDirectory parent, String revAddress, DeployDispatcher dispatcher, byte[] signingKey) {
        super(name, parent);
        this.revAddress = revAddress;
        this.deployDispatcher = dispatcher;
        this.signingKey = signingKey;
    }

    @Override
    public synchronized void addChild(IPath p) throws OperationNotPermitted {
        if (p instanceof TokenFile tokenFile) {
            long amount = tokenFile.value;
            String rholang = RholangExpressionConstructor.transfer(ConfigStorage.getRevAddress(), this.revAddress, amount);

            deployDispatcher.enqueueDeploy(new DeployDispatcher.Deployment(
                rholang, false, F1r3flyApi.RHOLANG,
                signingKey
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
    public @NotNull String getName() {
        if (isLocked) {
            return "LOCKED-REMOTE-REV-" + super.getName();
        } else {
            return super.getName();
        }
    }
}
