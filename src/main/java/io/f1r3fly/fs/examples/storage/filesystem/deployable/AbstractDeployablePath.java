package io.f1r3fly.fs.examples.storage.filesystem.deployable;

import io.f1r3fly.fs.examples.storage.DeployDispatcher;
import io.f1r3fly.fs.examples.storage.errors.OperationNotPermitted;
import io.f1r3fly.fs.examples.storage.grcp.F1r3flyApi;
import io.f1r3fly.fs.examples.storage.filesystem.common.AbstractPath;
import io.f1r3fly.fs.examples.storage.filesystem.common.Directory;
import io.f1r3fly.fs.examples.storage.rholang.RholangExpressionConstructor;

public abstract class AbstractDeployablePath extends AbstractPath {

    public AbstractDeployablePath(String name, Directory parent) {
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
    public void rename(String newName, Directory newParent) throws OperationNotPermitted {
        String oldPath = getAbsolutePath();
        super.rename(newName, newParent);
        String newPath = getAbsolutePath();
        enqueueMutation(RholangExpressionConstructor.renameChanel(oldPath, newPath));
    }

    public abstract DeployDispatcher getDeployDispatcher();
}
