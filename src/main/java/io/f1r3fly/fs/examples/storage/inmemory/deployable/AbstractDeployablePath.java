package io.f1r3fly.fs.examples.storage.inmemory.deployable;

import io.f1r3fly.fs.examples.storage.DeployDispatcher;
import io.f1r3fly.fs.examples.storage.errors.OperationNotPermitted;
import io.f1r3fly.fs.examples.storage.grcp.F1r3flyApi;
import io.f1r3fly.fs.examples.storage.inmemory.common.AbstractPath;
import io.f1r3fly.fs.examples.storage.inmemory.common.IDirectory;
import io.f1r3fly.fs.examples.storage.rholang.RholangExpressionConstructor;
import org.jetbrains.annotations.Nullable;

public abstract class AbstractDeployablePath extends AbstractPath {

    public AbstractDeployablePath(String name, IDirectory parent) {
        super(name, parent);
    }

    protected void enqueueMutation(String rholangExpression) {
        DeployDispatcher.Deployment deployment = new DeployDispatcher.Deployment(
            rholangExpression,
            true,
            F1r3flyApi.RHOLANG,
            getSigningKey()
        );

        getDeployDispatcher().enqueueDeploy(deployment);
    }

    protected abstract byte[] getSigningKey();

    @Override
    public synchronized void delete() {
        String rholangExpression = RholangExpressionConstructor.forgetChanel(getAbsolutePath());
        enqueueMutation(rholangExpression);
    }

    @Override
    public void rename(String newName, IDirectory newParent) throws OperationNotPermitted {
        String oldPath = getAbsolutePath();
        super.rename(newName, newParent);
        String newPath = getAbsolutePath();
        enqueueMutation(RholangExpressionConstructor.renameChanel(oldPath, newPath));
    }

    public abstract DeployDispatcher getDeployDispatcher();
}
