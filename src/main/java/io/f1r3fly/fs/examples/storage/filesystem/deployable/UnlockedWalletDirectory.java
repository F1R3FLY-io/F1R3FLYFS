package io.f1r3fly.fs.examples.storage.filesystem.deployable;

import io.f1r3fly.fs.examples.storage.DeployDispatcher;
import io.f1r3fly.fs.examples.storage.filesystem.common.Path;

import java.util.Set;

public class UnlockedWalletDirectory extends BlockchainDirectory {
    protected Set<Path> children;
    private String revAddress;
    private byte[] signingKey;
    private DeployDispatcher deployDispatcher;

    public UnlockedWalletDirectory(String revAddress, byte[] signingKey, Set<Path> children, DeployDispatcher deployDispatcher, boolean sendToShard) {
        super(revAddress, null, sendToShard);
        this.children = children;
        this.revAddress = revAddress;
        this.signingKey = signingKey;
        this.deployDispatcher = deployDispatcher;
    }

    @Override
    public DeployDispatcher getDeployDispatcher() {
        return this.deployDispatcher;
    }

    @Override
    public byte[] getSigningKey() {
        return this.signingKey;
    }

    // TODO: fix
//    @Override
//    public synchronized void addChild(Path p) throws OperationNotPermitted {
//        if (p instanceof TokenFile tokenFile) {
//            long amount = tokenFile.value;
//            String rholang = RholangExpressionConstructor.transfer(ConfigStorage.getRevAddress(), this.revAddress, amount);
//
//            deployDispatcher.enqueueDeploy(new DeployDispatcher.Deployment(
//                rholang, false, F1r3flyApi.RHOLANG,
//                signingKey
//            ));
//        } else {
//            throw OperationNotPermitted.instance;
//        }
//    }
}
