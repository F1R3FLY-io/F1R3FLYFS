package io.f1r3fly.fs.examples.storage.inmemory;

import io.f1r3fly.fs.examples.storage.DeployDispatcher;

import java.io.IOException;

public class TokenDirectory extends MemoryDirectory {

    public TokenDirectory(String prefix, String name, DeployDispatcher deployDispatcher, boolean sendToShard) {
        super(prefix, name, deployDispatcher, false);
    }

    public TokenDirectory(String prefix, String name, MemoryDirectory parent, DeployDispatcher deployDispatcher, boolean sendToShard) {
        super(prefix, name, parent, deployDispatcher, false);
    }

    @Override
    public synchronized MemoryDirectory mkdir(String lastComponent, boolean sendToShard) {
        throw new UnsupportedOperationException("TokenDirectory does not support mkdir");
    }

    @Override
    public synchronized void mkfile(String lastComponent, boolean sendToRChain) {
        throw new UnsupportedOperationException("TokenDirectory does not support mkfile");
    }

    @Override
    public synchronized void add(MemoryPath p, boolean sendToRChain) {
        super.add(p, false);
    }

    @Override
    public synchronized void deleteChild(MemoryPath child, boolean sendToRChain) {
        super.deleteChild(child, false);
    }

    @Override
    public void rename(String newName, MemoryDirectory newParent, boolean sendToShard) throws IOException {
        super.rename(newName, newParent, false);
    }
}
