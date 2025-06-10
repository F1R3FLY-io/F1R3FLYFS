package io.f1r3fly.f1r3drive.blockchain;

import io.f1r3fly.f1r3drive.blockchain.client.DeployDispatcher;
import io.f1r3fly.f1r3drive.blockchain.client.F1r3flyBlockchainClient;
import io.f1r3fly.f1r3drive.blockchain.wallet.RevWalletInfo;

public class BlockchainContext {
    private final RevWalletInfo walletInfo;
    private final DeployDispatcher deployDispatcher;

    public BlockchainContext(RevWalletInfo walletInfo, DeployDispatcher deployDispatcher) {
        this.walletInfo = walletInfo;
        this.deployDispatcher = deployDispatcher;
    }
    
    public RevWalletInfo getWalletInfo() {
        return walletInfo;
    }

    public DeployDispatcher getDeployDispatcher() {
        return deployDispatcher;
    }

    public F1r3flyBlockchainClient getBlockchainClient() {
        return deployDispatcher.getBlockchainClient();
    }
    
}
