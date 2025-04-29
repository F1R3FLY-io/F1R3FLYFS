package io.f1r3fly.fs.examples.storage.inmemory.common;

import io.f1r3fly.fs.examples.storage.errors.OperationNotPermitted;
import io.f1r3fly.fs.struct.FileStat;
import io.f1r3fly.fs.struct.FuseContext;
import io.f1r3fly.fs.utils.PathUtils;

import javax.annotation.Nullable;
import java.util.Arrays;

/**
 * Base class for all memory paths in the file system
 */
public abstract class AbstractPath implements IPath {

    protected String prefix;
    protected String name;
    protected IDirectory parent;

    public AbstractPath(String prefix, String name, IDirectory parent) {
        this.prefix = prefix;
        this.name = name;
        this.parent = parent;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getPrefix() {
        return prefix;
    }

    @Override
    public String getAbsolutePath() {
        if (parent == null) {
            return prefix + name;
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