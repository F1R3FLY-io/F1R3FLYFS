package io.f1r3fly.fs.examples;

import org.apache.commons.codec.binary.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OneTimeTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(F1r3flyFS.class);

    public static void main(String[] args) throws Exception {
        String privateKey = "f9854c5199bc86237206c75b25c6aeca024dccc0f55df3a553131111fd25dd85";
        byte[] privateKeyHex = Hex.decodeHex(privateKey.toCharArray());

    }
}
