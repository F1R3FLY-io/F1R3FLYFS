package io.f1r3fly.fs.examples.storage.inmemory.common;

import io.f1r3fly.fs.FuseFillDir;
import io.f1r3fly.fs.examples.storage.errors.OperationNotPermitted;
import io.f1r3fly.fs.struct.FileStat;
import io.f1r3fly.fs.struct.FuseContext;
import io.f1r3fly.fs.utils.PathUtils;
import jnr.ffi.Pointer;

import java.util.Set;


public interface IDirectory extends IPath {
    void mkdir(String lastComponent) throws OperationNotPermitted;

    void mkfile(String lastComponent) throws OperationNotPermitted;

    Set<IPath> getChildren();

    // Simplified find method for directories
    @Override
    default IPath find(String path) {
        path = normalizePath(path);
        
        // If path is empty or matches this directory name, return this directory
        if (path.isEmpty() || path.equals(getName())) {
            return this;
        }
        
        // If path doesn't contain separator, look for direct child
        if (!path.contains(separator())) {
            return findDirectChild(path);
        }
        
        // Split path into first component and rest
        int separatorIndex = path.indexOf(separator());
        String firstComponent = path.substring(0, separatorIndex);
        String remainingPath = path.substring(separatorIndex);
        
        // Find the first component child and recursively search in it
        IPath child = findDirectChild(firstComponent);
        return child != null ? child.find(remainingPath) : null;
    }

    // Helper method to find direct child by name
    default IPath findDirectChild(String name) {
        for (IPath child : getChildren()) {
            if (child.getName().equals(name)) {
                return child;
            }
        }
        return null;
    }

    default void read(Pointer buf, FuseFillDir filler) {
        for (IPath child : getChildren()) {
            filler.apply(buf, child.getName(), null, 0);
        }
    }

    default void getAttr(FileStat stat, FuseContext fuseContext) {
        stat.st_mode.set(FileStat.S_IFDIR | 0777);
        stat.st_uid.set(fuseContext.uid.get());
        stat.st_gid.set(fuseContext.gid.get());
    }

    default boolean isEmpty() {
        return getChildren().isEmpty();
    }

    void addChild(IPath child) throws OperationNotPermitted;

    void deleteChild(IPath child) throws OperationNotPermitted;

    default void cleanLocalCache() {
        getChildren().forEach(IPath::cleanLocalCache);
    }
}
