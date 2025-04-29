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

    default IPath find(String path) {
        if (IPath.super.find(path) != null) {
            return IPath.super.find(path);
        }
        while (path.startsWith(PathUtils.getPathDelimiterBasedOnOS())) {
            path = path.substring(PathUtils.getPathDelimiterBasedOnOS().length());
        }
        if (!path.contains(PathUtils.getPathDelimiterBasedOnOS())) {
            for (IPath p : getChildren()) {
                if (p.getName().equals(path)) {
                    return p;
                }
            }
            return null;
        }
        String nextName = path.substring(0, path.indexOf(PathUtils.getPathDelimiterBasedOnOS()));
        String rest = path.substring(path.indexOf(PathUtils.getPathDelimiterBasedOnOS()));
        for (IPath p : getChildren()) {
            if (p.getName().equals(nextName)) {
                return p.find(rest);
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
}
