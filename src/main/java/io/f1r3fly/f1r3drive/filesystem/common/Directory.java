package io.f1r3fly.f1r3drive.filesystem.common;

import io.f1r3fly.f1r3drive.fuse.FuseFillDir;
import io.f1r3fly.f1r3drive.errors.OperationNotPermitted;
import io.f1r3fly.f1r3drive.fuse.struct.FileStat;
import io.f1r3fly.f1r3drive.fuse.struct.FuseContext;
import jnr.ffi.Pointer;

import java.util.Set;


public interface Directory extends Path {
    void mkdir(String lastComponent) throws OperationNotPermitted;

    void mkfile(String lastComponent) throws OperationNotPermitted;

    Set<Path> getChildren();

    // Simplified find method for directories
    @Override
    default Path find(String path) {
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
        Path child = findDirectChild(firstComponent);
        return child != null ? child.find(remainingPath) : null;
    }

    // Helper method to find direct child by name
    default Path findDirectChild(String name) {
        for (Path child : getChildren()) {
            if (child.getName().equals(name)) {
                return child;
            }
        }
        return null;
    }

    default void read(Pointer buf, FuseFillDir filler) {
        for (Path child : getChildren()) {
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

    void addChild(Path child) throws OperationNotPermitted;

    void deleteChild(Path child) throws OperationNotPermitted;

    default void cleanLocalCache() {
        getChildren().forEach(Path::cleanLocalCache);
    }
}
