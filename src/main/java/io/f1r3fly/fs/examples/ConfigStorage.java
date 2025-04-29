package io.f1r3fly.fs.examples;

public class ConfigStorage {

    // TODO: make and singleton?
    private static byte[] privateKey;
    private static String revAddress;

    public static byte[] getPrivateKey() {
        return privateKey;
    }

    public static void setPrivateKey(byte[] privateKeyHex) {
        if (privateKeyHex == null || privateKeyHex.length == 0) {
            throw new IllegalArgumentException("Private key cannot be null or empty");
        }
        ConfigStorage.privateKey = privateKeyHex;
    }

    public static String getRevAddress() {
        if (ConfigStorage.revAddress == null) {
            throw new IllegalStateException("REV address not set");
        }
        return ConfigStorage.revAddress;
    }

    public static void setRevAddress(String revAddress) {
        if (revAddress == null || revAddress.isEmpty()) {
            throw new IllegalArgumentException("REV address cannot be null or empty");
        }
        ConfigStorage.revAddress = revAddress;
    }
}

