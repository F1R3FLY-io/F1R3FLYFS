package io.f1r3fly.fs.examples.storage.inmemory.common;

import io.f1r3fly.fs.examples.storage.errors.OperationNotPermitted;
import io.f1r3fly.fs.utils.PathUtils;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

/**
 * Base class for all memory paths in the file system
 */
public abstract class AbstractPath implements IPath {

    @NotNull protected String name;
    @Nullable protected IDirectory parent;

    public AbstractPath(@NotNull String name, @Nullable IDirectory parent) {
        this.name = name;
        this.parent = parent;
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
            return parent.getAbsolutePath() + PathUtils.getPathDelimiterBasedOnOS() + name;
        }
    }

    @Override
    public @Nullable IDirectory getParent() {
        return parent;
    }

    @Override
    public void rename(String newName, IDirectory newParent) throws OperationNotPermitted {
        this.name = newName;
        this.parent = newParent;
    }
}