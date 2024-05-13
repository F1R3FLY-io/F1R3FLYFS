package io.f1r3fly.fs.examples.datatransformer;

public class Base64Coder {

    public static String encodeToString(byte[] data) {
        return java.util.Base64.getEncoder().encodeToString(data);
    }

    public static byte[] decodeFromString(String data) {
        return java.util.Base64.getDecoder().decode(data);
    }
}
