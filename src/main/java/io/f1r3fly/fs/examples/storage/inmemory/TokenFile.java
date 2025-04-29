package io.f1r3fly.fs.examples.storage.inmemory;

import io.f1r3fly.fs.examples.storage.DeployDispatcher;
import io.f1r3fly.fs.struct.FileStat;
import io.f1r3fly.fs.struct.FuseContext;
import jnr.ffi.Pointer;

import java.io.IOException;
import java.util.Map;

public class TokenFile extends MemoryFile {

    public final long value;

    public TokenFile(String prefix, String name, MemoryDirectory parent, DeployDispatcher deployDispatcher, boolean sendToShard, long value) {
        super(prefix, name, parent, deployDispatcher, false);
        this.value = value;
    }

    public long getValue() {
        return value;
    }

    @Override
    public void open() { /* do nothing */ }
    @Override
    public void close() { /* do nothing */ }

    @Override
    long getSize() {
        return 0;
    }

    @Override
    public int write(Pointer buffer, long bufSize, long writeOffset) throws IOException { return 0; }

    @Override
    public int read(Pointer buffer, long size, long offset) throws IOException { return 0; }

    @Override
    public void onChange() { /* do nothing */ }
    
    @Override
    public void rename(String newName, MemoryDirectory newParent, boolean sendToShard) throws IOException {
        super.rename(newName, newParent, false);
    }
    
    @Override
    public void getattr(FileStat stat, FuseContext fuseContext) {
        super.getattr(stat, fuseContext);
    }
    
    @Override
    public synchronized void truncate(long offset) throws IOException { /* do nothing */ }
    
    @Override
    public int initFromBytes(byte[] bytes, long offset) throws IOException { return 0; }
    
    @Override
    public void initSubChannels(Map<Integer, String> subChannels) { /* do nothing */ }
}
