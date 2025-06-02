package io.f1r3fly.f1r3drive.filesystem.deployable;

import io.f1r3fly.f1r3drive.filesystem.common.Directory;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FetchedFile extends BlockchainFile {

  public FetchedFile(String name, Directory parent) {
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
