package io.f1r3fly.fs.examples.storage.inmemory.common;

import io.f1r3fly.fs.examples.storage.errors.OperationNotPermitted;
import io.f1r3fly.fs.struct.FileStat;
import io.f1r3fly.fs.struct.FuseContext;
import io.f1r3fly.fs.utils.PathUtils;

import javax.annotation.Nullable;

public interface IPath {
    void getAttr(FileStat stat, FuseContext fuseContext);

    default IPath find(String path) {
        while (path.startsWith(PathUtils.getPathDelimiterBasedOnOS())) {
            path = path.substring(PathUtils.getPathDelimiterBasedOnOS().length());
        }
        if (path.equals(getName()) || path.isEmpty()) {
            return this;
        }
        return null;
    }

    String getName();

    String getPrefix();

    String getAbsolutePath();

    @Nullable
    IDirectory getParent();

    void delete() throws OperationNotPermitted;

    void rename(String newName, IDirectory newParent) throws OperationNotPermitted;
}
