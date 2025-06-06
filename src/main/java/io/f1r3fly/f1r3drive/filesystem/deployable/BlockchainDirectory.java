package io.f1r3fly.f1r3drive.filesystem.deployable;

import io.f1r3fly.f1r3drive.blockchain.client.DeployDispatcher;
import io.f1r3fly.f1r3drive.filesystem.common.Directory;
import io.f1r3fly.f1r3drive.filesystem.common.Path;
import io.f1r3fly.f1r3drive.blockchain.rholang.RholangExpressionConstructor;
import io.f1r3fly.f1r3drive.blockchain.wallet.RevWalletInfo;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BlockchainDirectory extends AbstractDeployablePath implements Directory {
    
    private static final Logger logger = LoggerFactory.getLogger(BlockchainDirectory.class);
    
    protected Set<Path> children = new HashSet<>();

    public BlockchainDirectory(String name, BlockchainDirectory parent) {
        this(name, parent, true);
    }

    protected BlockchainDirectory(String name, Directory parent, boolean sendToShard) {
        super(name, parent);
        if (sendToShard) {
            String rholang = RholangExpressionConstructor.sendDirectoryIntoNewChannel(getAbsolutePath(), Set.of());
            enqueueMutation(rholang);
        }
    }

    @Override
    public synchronized void addChild(Path p) {
        // force re-add
        
        // First remove any existing child with the same name
        boolean removed = children.removeIf(child -> child.getName().equals(p.getName()));
        if (removed) {
            logger.info("Removed existing child with name %s".formatted(p.getName()));
        }
        
        // Then add the new child
        boolean added = children.add(p);

        if (added) {
            enqueueUpdatingChildrenList();
        }
    }

    private void enqueueUpdatingChildrenList() {
        Set<String> newChildren =
            children.stream()
                .filter((x) -> x instanceof AbstractDeployablePath)
                .map(Path::getName)
                .collect(Collectors.toSet());
        String rholang = RholangExpressionConstructor.updateChildren(
            getAbsolutePath(),
            newChildren
        );

        enqueueMutation(rholang);
    }

    @Override
    public synchronized void deleteChild(Path child) {
        children.remove(child);

        enqueueUpdatingChildrenList();
    }

    @Override
    public synchronized void mkdir(String lastComponent) {
        BlockchainDirectory newDir = new BlockchainDirectory(lastComponent, this, true);
        addChild(newDir);
    }

    @Override
    public synchronized void mkfile(String lastComponent) {
        BlockchainFile memoryFile = new BlockchainFile(lastComponent, this);
        addChild(memoryFile);
    }

    @Override
    public Set<Path> getChildren() {
        return children; //TODO: return immutable set?
    }

    //TODO: DRY
    @Override
    public DeployDispatcher getDeployDispatcher() {
        Directory parent = getParent();
        if (parent instanceof BlockchainDirectory) {
            return ((BlockchainDirectory) parent).getDeployDispatcher();
        } else if (parent == null) {
            throw new IllegalStateException("Parent is null");
        } else {
            throw new IllegalStateException("deployable path %s depends on non-deployable parent %s".formatted(name, parent.getName()));
        }
    }

    @Override
    public RevWalletInfo getRevWalletInfo() {
        Directory parent = getParent();
        if (parent instanceof BlockchainDirectory) {
            return ((BlockchainDirectory) parent).getRevWalletInfo();
        } else if (parent == null) {
            throw new IllegalStateException("Parent is null");
        } else {
            throw new IllegalStateException("deployable path %s depends on non-deployable parent %s".formatted(name, parent.getName()));
        }
    }

}