package io.f1r3fly.fs.examples.storage.inmemory.deployable;

import io.f1r3fly.fs.examples.storage.DeployDispatcher;
import io.f1r3fly.fs.examples.storage.errors.OperationNotPermitted;
import io.f1r3fly.fs.examples.storage.grcp.F1r3flyApi;
import io.f1r3fly.fs.examples.storage.inmemory.common.AbstractPath;
import io.f1r3fly.fs.examples.storage.inmemory.common.IDirectory;
import io.f1r3fly.fs.examples.storage.rholang.RholangExpressionConstructor;
import io.f1r3fly.fs.utils.PathUtils;

import java.io.IOException;

public abstract class AbstractDeployablePath extends AbstractPath {

    protected DeployDispatcher deployDispatcher;

    public AbstractDeployablePath(String prefix, String name, IDirectory parent, DeployDispatcher deployDispatcher) {
        super(prefix, name, parent);
        this.deployDispatcher = deployDispatcher;
    }

    protected void enqueueMutation(String rholangExpression) {
        DeployDispatcher.Deployment deployment = new DeployDispatcher.Deployment(
            rholangExpression,
            true,
            F1r3flyApi.RHOLANG
        );

        this.deployDispatcher.enqueueDeploy(deployment);
    }

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
} 