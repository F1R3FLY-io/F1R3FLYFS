package io.f1r3fly.fs.examples.storage.inmemory.common;

import io.f1r3fly.fs.examples.storage.errors.OperationNotPermitted;
import io.f1r3fly.fs.struct.FileStat;
import io.f1r3fly.fs.struct.FuseContext;
import io.f1r3fly.fs.utils.PathUtils;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

public interface IPath {
    void getAttr(FileStat stat, FuseContext fuseContext);

    // Helper method to get path separator
    default String separator() {
        return PathUtils.getPathDelimiterBasedOnOS();
    }

    // Helper method to normalize path by removing leading separators
    default String normalizePath(String path) {
        while (path.startsWith(separator())) {
            path = path.substring(separator().length());
        }
        return path;
    }

    // Simplified find method - only handles current path matching
    default IPath find(String path) {
        path = normalizePath(path);
        if (path.equals(getName()) || path.isEmpty()) {
            return this;
        }
        return null;
    }

    @NotNull String getName();

    @NotNull String getAbsolutePath();

    @Nullable
    IDirectory getParent();

    void delete() throws OperationNotPermitted;

    void rename(String newName, IDirectory newParent) throws OperationNotPermitted;

    default void cleanLocalCache() {};
}
