package io.f1r3fly.fs.examples.storage.inmemory.deployable;

import io.f1r3fly.fs.examples.storage.DeployDispatcher;
import io.f1r3fly.fs.examples.storage.inmemory.common.IDirectory;
import io.f1r3fly.fs.examples.storage.inmemory.common.IPath;
import io.f1r3fly.fs.examples.storage.rholang.RholangExpressionConstructor;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class InMemoryDirectory extends AbstractDeployablePath implements IDirectory {
    protected Set<IPath> children = new HashSet<>();

    public InMemoryDirectory(String prefix, String name, InMemoryDirectory parent, DeployDispatcher deployDispatcher) {
        this(prefix, name, parent, deployDispatcher, true);
    }

    protected InMemoryDirectory(String prefix, String name, InMemoryDirectory parent, DeployDispatcher deployDispatcher, boolean sendToShard) {
        super(prefix, name, parent, deployDispatcher);
        if (sendToShard) {
            String rholang = RholangExpressionConstructor.sendDirectoryIntoNewChannel(getAbsolutePath(), Set.of());
            enqueueMutation(rholang);
        }
    }

    @Override
    public synchronized void addChild(IPath p) {
        if (!children.contains(p)) {
            children.add(p);
        }

        enqueueUpdatingChildrenList();
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
        InMemoryDirectory newDir = new InMemoryDirectory(prefix, lastComponent, this, deployDispatcher, true);
        addChild(newDir);
    }

    @Override
    public synchronized void mkfile(String lastComponent) {
        InMemoryFile memoryFile = new InMemoryFile(prefix, lastComponent, this, deployDispatcher);
        addChild(memoryFile);
    }

    @Override
    public Set<IPath> getChildren() {
        return children; //TODO: return immutable set?
    }
}