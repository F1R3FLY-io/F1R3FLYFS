package io.f1r3fly.f1r3drive.background.state;

public interface StateChangeEvents {
    record WalletBalanceChanged(String revAddress) implements StateChangeEvents {}


}

