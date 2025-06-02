package io.f1r3fly.fs.examples.storage.filesystem.common;

import io.f1r3fly.fs.struct.FileStat;
import io.f1r3fly.fs.struct.FuseContext;
import jnr.ffi.Pointer;

import java.io.IOException;

public interface File extends Path {
    int read(Pointer buffer, long size, long offset) throws IOException;

    int write(Pointer buffer, long bufSize, long writeOffset) throws IOException, UnsupportedOperationException;

    void truncate(long offset) throws IOException;

    default long getSize() {
        return 0;
    }

    default void getAttr(FileStat stat, FuseContext fuseContext) {
        stat.st_mode.set(FileStat.S_IFREG | 0777);
        stat.st_size.set(getSize());
        stat.st_uid.set(fuseContext.uid.get());
        stat.st_gid.set(fuseContext.gid.get());
    }

    void open() throws IOException;

    void close();
}