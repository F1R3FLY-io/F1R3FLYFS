package io.f1r3fly.f1r3drive.filesystem.deployable;

import io.f1r3fly.f1r3drive.blockchain.BlockchainContext;
import io.f1r3fly.f1r3drive.blockchain.client.DeployDispatcher;
import io.f1r3fly.f1r3drive.blockchain.client.F1r3flyBlockchainClient;
import io.f1r3fly.f1r3drive.blockchain.wallet.RevWalletInfo;
import io.f1r3fly.f1r3drive.filesystem.common.Directory;
import io.f1r3fly.f1r3drive.filesystem.common.Path;
import io.f1r3fly.f1r3drive.blockchain.rholang.RholangExpressionConstructor;
import io.f1r3fly.f1r3drive.errors.OperationNotPermitted;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import io.f1r3fly.f1r3drive.filesystem.local.TokenDirectory;
import io.f1r3fly.f1r3drive.filesystem.local.TokenFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BlockchainDirectory extends AbstractDeployablePath implements Directory {

    private static final Logger logger = LoggerFactory.getLogger(BlockchainDirectory.class);

    protected Set<Path> children = new HashSet<>();

    public BlockchainDirectory(BlockchainContext blockchainContext, String name, BlockchainDirectory parent) {
        this(blockchainContext, name, parent, true);
    }

    protected BlockchainDirectory(BlockchainContext blockchainContext, String name, Directory parent,
            boolean sendToShard) {
        super(blockchainContext, name, parent);
        if (sendToShard) {
            String rholang = RholangExpressionConstructor.sendDirectoryIntoNewChannel(getAbsolutePath(), Set.of(), getLastUpdated());
            enqueueMutation(rholang);
        }
    }

    @Override
    public synchronized void addChild(Path p) throws OperationNotPermitted {
        if (p instanceof TokenFile tokenFile) {
            long amount = tokenFile.getValue();

            TokenDirectory tokenDirectoryFrom = tokenFile.getParent();

            if (tokenDirectoryFrom == null) {
                throw new IllegalArgumentException(
                        "Token directory does not exist in token file %s".formatted(tokenFile.getAbsolutePath()));
            }

            RevWalletInfo walletInfoFrom = tokenDirectoryFrom.getBlockchainContext().getWalletInfo();
            RevWalletInfo walletInfoTo = this.getBlockchainContext().getWalletInfo();

            if (walletInfoFrom.revAddress().equals(walletInfoTo.revAddress())) {
                return;
            }

            String rholang = RholangExpressionConstructor.transfer(walletInfoFrom.revAddress(),
                    walletInfoTo.revAddress(), amount);

            // TODO: need to send notification revTo changed as well
            getBlockchainContext().getDeployDispatcher().enqueueDeploy(new DeployDispatcher.Deployment(
                    rholang, true, F1r3flyBlockchainClient.RHOLANG, walletInfoFrom.revAddress(),
                    walletInfoFrom.signingKey(),
                    System.currentTimeMillis()));
        } else {

            // force re-add

            // First remove any existing child with the same name
            boolean removed = children.removeIf(child -> child.getName().equals(p.getName()));
            if (removed) {
                logger.info("Removed existing child with name %s".formatted(p.getName()));
            }

            // Then add the new child
            boolean added = children.add(p);

            if (added) {
                this.refreshLastUpdated();
                enqueueUpdatingChildrenList();
            }
        }
    }

    private void enqueueUpdatingChildrenList() {
        Set<String> newChildren = children.stream()
                .filter((x) -> x instanceof AbstractDeployablePath)
                .map(Path::getName)
                .collect(Collectors.toSet());
        String rholang = RholangExpressionConstructor.updateChildren(
                getAbsolutePath(),
                newChildren,
                getLastUpdated());

        enqueueMutation(rholang);
    }

    @Override
    public synchronized void deleteChild(Path child) {
        children.remove(child);

        refreshLastUpdated();
        enqueueUpdatingChildrenList();
    }

    @Override
    public synchronized void mkdir(String lastComponent) throws OperationNotPermitted {
        BlockchainDirectory newDir = new BlockchainDirectory(getBlockchainContext(), lastComponent, this, true);
        addChild(newDir);
    }

    @Override
    public synchronized void mkfile(String lastComponent) throws OperationNotPermitted {
        BlockchainFile memoryFile = new BlockchainFile(getBlockchainContext(), lastComponent, this);
        addChild(memoryFile);
    }

    @Override
    public Set<Path> getChildren() {
        return children; // TODO: return immutable set?
    }

}