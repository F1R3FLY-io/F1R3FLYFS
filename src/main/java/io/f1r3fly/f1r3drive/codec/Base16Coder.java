package io.f1r3fly.f1r3drive.codec;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;

public class Base16Coder {
    public static String bytesToHex(byte[] byteArray) {
        return Hex.encodeHexString(byteArray);
    }
    public static byte[] hexToBytes(String hex) throws DecoderException {
        return Hex.decodeHex(hex);
    }
}
