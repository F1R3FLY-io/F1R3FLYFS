package io.f1r3fly.f1r3drive.filesystem.deployable;

import io.f1r3fly.f1r3drive.filesystem.common.Path;
import io.f1r3fly.f1r3drive.blockchain.rholang.RholangExpressionConstructor;
import io.f1r3fly.f1r3drive.filesystem.local.RootDirectory;
import io.f1r3fly.f1r3drive.filesystem.local.TokenDirectory;
import io.f1r3fly.f1r3drive.blockchain.BlockchainContext;

import java.util.HashSet;
import java.util.Set;

public class UnlockedWalletDirectory extends BlockchainDirectory {

    public UnlockedWalletDirectory(BlockchainContext blockchainContext, Set<Path> children, RootDirectory parent, boolean sendToShard) {
        super(blockchainContext, blockchainContext.getWalletInfo().revAddress(), parent, false);

        if (sendToShard && !children.isEmpty()) {
            // if sendToShard is true, it's a new folder, so children must be empty
            throw new IllegalArgumentException("Children must be empty when new folder created");
        }

        if (sendToShard) {
            this.lastUpdated = System.currentTimeMillis() / 1000;
            String rholang = RholangExpressionConstructor.sendDirectoryIntoNewChannel(getAbsolutePath(), Set.of(), getLastUpdated());
            enqueueMutation(rholang);
        }

        // create token directory
        TokenDirectory tokenDirectory = new TokenDirectory(this.getBlockchainContext(), this);
        this.children = new HashSet<>(children);
        this.children.add(tokenDirectory);
    }

    public TokenDirectory getTokenDirectory() {
        return (TokenDirectory) this.children.stream()
                .filter(p -> p instanceof TokenDirectory)
                .findFirst()
                .orElse(null);
    }
}
