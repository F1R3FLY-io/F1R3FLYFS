package io.f1r3fly.f1r3drive.filesystem.common;

import io.f1r3fly.f1r3drive.blockchain.BlockchainContext;
import io.f1r3fly.f1r3drive.errors.OperationNotPermitted;
import io.f1r3fly.f1r3drive.filesystem.utils.PathUtils;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

/**
 * Base class for all memory paths in the file system
 */
public abstract class AbstractPath implements Path {

    @NotNull protected BlockchainContext blockchainContext;
    @NotNull protected String name;
    @Nullable protected Directory parent;
    @NotNull protected Long lastUpdated;

    public AbstractPath(@NotNull BlockchainContext blockchainContext, @NotNull String name, @Nullable Directory parent) {
        this.name = name;
        this.parent = parent;
        this.blockchainContext = blockchainContext;
        this.lastUpdated = System.currentTimeMillis() / 1000;
    }

    @Override
    public @NotNull String getName() {
        return name;
    }

    @Override
    public @NotNull String getAbsolutePath() {
        if (parent == null) {
            return name;
        } else {
            String parentPath = parent.getAbsolutePath();
            String delimiter = PathUtils.getPathDelimiterBasedOnOS();
            
            // Avoid double separators if parent path already ends with separator
            if (parentPath.endsWith(delimiter)) {
                return parentPath + name;
            } else {
                return parentPath + delimiter + name;
            }
        }
    }

    @Override
    public @Nullable Directory getParent() {
        return parent;
    }

    @Override
    public void rename(String newName, Directory newParent) throws OperationNotPermitted {
        this.name = newName;
        this.parent = newParent;
    }

    @Override
    public BlockchainContext getBlockchainContext() {
        return blockchainContext;
    }

    @Override
    public @NotNull Long getLastUpdated() {
        return lastUpdated;
    }
}