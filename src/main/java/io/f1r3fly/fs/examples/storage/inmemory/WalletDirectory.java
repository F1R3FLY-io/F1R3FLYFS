package io.f1r3fly.fs.examples.storage.inmemory;

import io.f1r3fly.fs.FuseFillDir;
import io.f1r3fly.fs.examples.ConfigStorage;
import io.f1r3fly.fs.examples.storage.DeployDispatcher;
import io.f1r3fly.fs.examples.storage.rholang.RholangExpressionConstructor;
import jnr.ffi.Pointer;

public class WalletDirectory extends MemoryDirectory {

    private String ravAddress;
    private long balance; //TODO: need it?

    private WalletDirectory(String prefix, String name, DeployDispatcher deployDispatcher) {
        super(prefix, name, deployDispatcher, false);
    }

    public WalletDirectory(String prefix, String name, MemoryDirectory parent, DeployDispatcher deployDispatcher,
            String ravAddress) {
        super(prefix, name, parent, deployDispatcher, false);
        this.ravAddress = ravAddress;
    }

    @Override
    public synchronized void add(MemoryPath p, boolean sendToRChain) {
        if (p instanceof TokenFile) {
            TokenFile tokenFile = (TokenFile) p;
            long amount = tokenFile.getValue();
            balance += amount;
            String rholang = RholangExpressionConstructor.transfer(ConfigStorage.getRevAddress(), ravAddress, balance);
            enqueueMutation(rholang);
        } else {
            throw new UnsupportedOperationException("WalletDirectory does not support add " + p.getClass());
        }
    }

    @Override
    public synchronized void deleteChild(MemoryPath child, boolean sendToRChain) {
        throw new UnsupportedOperationException("WalletDirectory does not support deleteChild");
    }

    @Override
    public synchronized MemoryDirectory mkdir(String lastComponent, boolean sendToShard) {
        throw new UnsupportedOperationException("WalletDirectory does not support mkdir");
    }

    @Override
    public synchronized void mkfile(String lastComponent, boolean sendToRChain) {
        throw new UnsupportedOperationException("WalletDirectory does not support mkfile");
    }

    @Override
    public synchronized void read(Pointer buf, FuseFillDir filler) {
        // do nothing
        // simulate empty directory
    }

    @Override
    public boolean isEmpty() {
        return true;
    }

    
}
