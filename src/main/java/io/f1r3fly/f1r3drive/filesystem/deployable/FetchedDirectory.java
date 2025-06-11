package io.f1r3fly.f1r3drive.filesystem.deployable;

import io.f1r3fly.f1r3drive.blockchain.BlockchainContext;
import io.f1r3fly.f1r3drive.filesystem.common.Path;

import java.util.Set;

public class FetchedDirectory extends BlockchainDirectory {
    public FetchedDirectory(BlockchainContext blockchainContext, String name, BlockchainDirectory parent) {
        super(blockchainContext, name, parent, false);
    }

    public void setChildren(Set<Path> children) {
        this.children = children;
    }

    public void updateParent(BlockchainDirectory parent) {
        this.parent = parent;
    }
}