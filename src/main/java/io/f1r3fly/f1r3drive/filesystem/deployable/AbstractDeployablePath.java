package io.f1r3fly.f1r3drive.filesystem.deployable;

import io.f1r3fly.f1r3drive.blockchain.client.DeployDispatcher;
import io.f1r3fly.f1r3drive.errors.OperationNotPermitted;
import io.f1r3fly.f1r3drive.blockchain.client.F1r3flyBlockchainClient;
import io.f1r3fly.f1r3drive.filesystem.common.AbstractPath;
import io.f1r3fly.f1r3drive.filesystem.common.Directory;
import io.f1r3fly.f1r3drive.blockchain.rholang.RholangExpressionConstructor;
import io.f1r3fly.f1r3drive.blockchain.wallet.RevWalletInfo;

public abstract class AbstractDeployablePath extends AbstractPath {

    public AbstractDeployablePath(String name, Directory parent) {
        super(name, parent);
    }

    protected void enqueueMutation(String rholangExpression) {

        RevWalletInfo revWalletInfo = getRevWalletInfo();
        DeployDispatcher.Deployment deployment = new DeployDispatcher.Deployment(
            rholangExpression,
            true,
            F1r3flyBlockchainClient.RHOLANG,
            revWalletInfo.revAddress(),
            revWalletInfo.signingKey()
        );

        getDeployDispatcher().enqueueDeploy(deployment);
    }

    
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

    protected abstract RevWalletInfo getRevWalletInfo();
    public abstract DeployDispatcher getDeployDispatcher();
}
