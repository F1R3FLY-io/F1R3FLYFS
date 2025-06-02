package io.f1r3fly.fs.examples.storage.inmemory.deployable;

import io.f1r3fly.fs.examples.storage.DeployDispatcher;
import io.f1r3fly.fs.examples.storage.inmemory.common.IPath;

import java.util.Set;

public class UnlockedRemoteDirectory extends InMemoryDirectory {
    protected Set<IPath> children;
    private String revAddress;
    private byte[] signingKey;
    private DeployDispatcher deployDispatcher;

    public UnlockedRemoteDirectory(String revAddress, byte[] signingKey, Set<IPath> children, DeployDispatcher deployDispatcher, boolean sendToShard) {
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
}
