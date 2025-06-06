package io.f1r3fly.f1r3drive.filesystem.deployable;

import io.f1r3fly.f1r3drive.blockchain.client.DeployDispatcher;
import io.f1r3fly.f1r3drive.filesystem.common.Path;
import io.f1r3fly.f1r3drive.blockchain.rholang.RholangExpressionConstructor;
import io.f1r3fly.f1r3drive.filesystem.local.RootDirectory;
import io.f1r3fly.f1r3drive.filesystem.local.TokenDirectory;
import io.f1r3fly.f1r3drive.blockchain.wallet.RevWalletInfo;

import java.util.HashSet;
import java.util.Set;

public class UnlockedWalletDirectory extends BlockchainDirectory {
    private String revAddress;
    private byte[] signingKey;
    private DeployDispatcher deployDispatcher;

    public UnlockedWalletDirectory(String revAddress, byte[] signingKey, Set<Path> children, RootDirectory parent, DeployDispatcher deployDispatcher, boolean sendToShard) {
        super(revAddress, parent, false);
        this.revAddress = revAddress;
        this.signingKey = signingKey;
        this.deployDispatcher = deployDispatcher;

        if (sendToShard && !children.isEmpty()) {
            // if sendToShard is true, it's a new folder, so children must be empty
            throw new IllegalArgumentException("Children must be empty when new folder created");
        }
        
        if (sendToShard) {
            String rholang = RholangExpressionConstructor.sendDirectoryIntoNewChannel(getAbsolutePath(), Set.of());
            enqueueMutation(rholang);
        }
        
        // create token directory
        TokenDirectory tokenDirectory = new TokenDirectory(this, deployDispatcher.getBlockchainClient());
        this.children = new HashSet<>(children);
        this.children.add(tokenDirectory);
    }


    public String getRevAddress() {
        return this.revAddress;
    }

    @Override
    public DeployDispatcher getDeployDispatcher() {
        return this.deployDispatcher;
    }

    @Override
    public RevWalletInfo getRevWalletInfo() {
        return new RevWalletInfo(this.revAddress, this.signingKey);
    }


    public TokenDirectory getTokenDirectory() {
        return (TokenDirectory) this.children.stream()
            .filter(p -> p instanceof TokenDirectory)
            .findFirst()
            .orElse(null);
    }

    // TODO: fix
//    @Override
//    public synchronized void addChild(Path p) throws OperationNotPermitted {
//        if (p instanceof TokenFile tokenFile) {
//            long amount = tokenFile.value;
//            String rholang = RholangExpressionConstructor.transfer(ConfigStorage.getRevAddress(), this.revAddress, amount);
//
//            deployDispatcher.enqueueDeploy(new DeployDispatcher.Deployment(
//                rholang, false, F1r3flyBlockchainClient.RHOLANG,
//                signingKey
//            ));
//        } else {
//            throw OperationNotPermitted.instance;
//        }
//    }
}
