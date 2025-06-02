package io.f1r3fly.f1r3drive.filesystem.deployable;

import io.f1r3fly.f1r3drive.filesystem.common.Path;

import java.util.Set;

public class FetchedDirectory extends BlockchainDirectory {
    public FetchedDirectory(String name, BlockchainDirectory parent) {
        super(name, parent, false);
    }

    public void setChildren(Set<Path> children) {
        this.children = children;
    }
}