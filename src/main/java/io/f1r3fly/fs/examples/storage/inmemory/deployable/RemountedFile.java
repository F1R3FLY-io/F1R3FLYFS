package io.f1r3fly.fs.examples.storage.inmemory.deployable;

import io.f1r3fly.fs.examples.storage.DeployDispatcher;
import io.f1r3fly.fs.examples.storage.inmemory.deployable.InMemoryFile;
import io.f1r3fly.fs.examples.storage.inmemory.common.IDirectory;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RemountedFile extends InMemoryFile {

  public RemountedFile(String name, IDirectory parent) {
        super(name, parent, false);
    }

    public int initFromBytes(byte[] bytes, long offset) throws IOException {
        open();
        synchronized (this) {
            rif.seek(offset);
            rif.write(bytes);
        }
        return bytes.length;
    }

    public void initSubChannels(Map<Integer, String> subChannels) {
        otherChunks = new ConcurrentHashMap<>(subChannels);
    }


}
