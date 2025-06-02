package io.f1r3fly.fs.examples.storage.inmemory.deployable;

import io.f1r3fly.fs.examples.storage.DeployDispatcher;
import io.f1r3fly.fs.examples.storage.inmemory.common.IDirectory;
import io.f1r3fly.fs.examples.storage.inmemory.common.IPath;
import io.f1r3fly.fs.examples.storage.inmemory.notdeployable.WalletDirectory;
import io.f1r3fly.fs.examples.storage.rholang.RholangExpressionConstructor;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class InMemoryDirectory extends AbstractDeployablePath implements IDirectory {
    protected Set<IPath> children = new HashSet<>();

    public InMemoryDirectory(String name, InMemoryDirectory parent) {
        this(name, parent, true);
    }

    protected InMemoryDirectory(String name, InMemoryDirectory parent, boolean sendToShard) {
        super(name, parent);
        if (sendToShard) {
            String rholang = RholangExpressionConstructor.sendDirectoryIntoNewChannel(getAbsolutePath(), Set.of());
            enqueueMutation(rholang);
        }
    }

    @Override
    public synchronized void addChild(IPath p) {
        boolean added = children.add(p);

        if (added) {
            enqueueUpdatingChildrenList();
        }
    }

    private void enqueueUpdatingChildrenList() {
        Set<String> newChildren =
            children.stream()
                .filter((x) -> x instanceof AbstractDeployablePath)
                .map(IPath::getName)
                .collect(Collectors.toSet());
        String rholang = RholangExpressionConstructor.updateChildren(
            getAbsolutePath(),
            newChildren
        );

        enqueueMutation(rholang);
    }

    @Override
    public synchronized void deleteChild(IPath child) {
        children.remove(child);

        enqueueUpdatingChildrenList();
    }

    @Override
    public synchronized void mkdir(String lastComponent) {
        InMemoryDirectory newDir = new InMemoryDirectory(lastComponent, this, true);
        addChild(newDir);
    }

    @Override
    public synchronized void mkfile(String lastComponent) {
        InMemoryFile memoryFile = new InMemoryFile(lastComponent, this);
        addChild(memoryFile);
    }

    @Override
    public Set<IPath> getChildren() {
        return children; //TODO: return immutable set?
    }

    //TODO: DRY
    @Override
    public DeployDispatcher getDeployDispatcher() {
        IDirectory parent = getParent();
        if (parent instanceof InMemoryDirectory) {
            return ((InMemoryDirectory) parent).getDeployDispatcher();
        } else if (parent == null) {
            throw new IllegalStateException("Parent is null");
        } else {
            throw new IllegalStateException("deployable path %s depends on non-deployable parent %s".formatted(name, parent.getName()));
        }
    }

    @Override
    public byte[] getSigningKey() {
        IDirectory parent = getParent();
        if (parent instanceof InMemoryDirectory) {
            return ((InMemoryDirectory) parent).getSigningKey();
        } else if (parent == null) {
            throw new IllegalStateException("Parent is null");
        } else {
            throw new IllegalStateException("deployable path %s depends on non-deployable parent %s".formatted(name, parent.getName()));
        }
    }

}