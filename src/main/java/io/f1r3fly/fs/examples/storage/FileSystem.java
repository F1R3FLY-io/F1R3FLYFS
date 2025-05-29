package io.f1r3fly.fs.examples.storage;

import io.f1r3fly.fs.examples.storage.inmemory.common.IDirectory;
import io.f1r3fly.fs.examples.storage.inmemory.common.IFile;
import io.f1r3fly.fs.examples.storage.inmemory.common.IPath;

import javax.annotation.Nullable;

public interface FileSystem {

    IPath getRootPath();
    IFile getFile(String path);
    IDirectory getDirectory(String path);

    boolean isRootPath(String path);


    @Nullable
    default IDirectory getParentDirectory(String path) {

        if (isRootPath(path)) {
            return null;
        }

        String parentPath = getParentPath(path);
        if (parentPath == null) {
            return null;
        }

        return getDirectory(parentPath);
    }

    @Nullable
    String getParentPath(String path);



}
