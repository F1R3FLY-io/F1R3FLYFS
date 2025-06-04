package io.f1r3fly.f1r3drive.crypto;

import com.rfksystems.blake2b.Blake2b;
import com.rfksystems.blake2b.security.Blake2bProvider;
import fr.acinq.secp256k1.Hex;
import fr.acinq.secp256k1.Secp256k1;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.apache.commons.codec.binary.Base64;

import java.math.BigInteger;
import java.util.Arrays;

/**
 * Utility class for validating private keys against REV addresses
 */
public class PrivateKeyValidator {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(PrivateKeyValidator.class);
    
    static {
        Security.addProvider(new Blake2bProvider());
        Security.addProvider(new BouncyCastleProvider());
    }
    
    // Prefix as defined in TypeScript implementation
    private static final String COIN_ID = "000000";
    private static final String VERSION = "00";
    
    // Base58 alphabet used for encoding
    private static final String ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";
    private static final BigInteger BASE = BigInteger.valueOf(58);
    
    /**
     * Exception thrown when private key validation fails
     */
    public static class InvalidPrivateKeyException extends RuntimeException {
        public InvalidPrivateKeyException(String message) {
            super(message);
        }
        
        public InvalidPrivateKeyException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    /**
     * Validates if a private key corresponds to the given REV address
     * @param privateKey The private key to validate
     * @param expectedRevAddress The expected REV address
     * @return true if the private key generates the expected REV address, false otherwise
     */
    public static boolean validatePrivateKeyForRevAddress(byte[] privateKey, String expectedRevAddress) {
        try {
            // Derive REV address from private key
            String derivedRevAddress = deriveRevAddressFromPrivateKey(privateKey);
            
            return expectedRevAddress.equals(derivedRevAddress);
        } catch (Exception e) {
            LOGGER.warn("Failed to validate private key for REV address", e);
            return false;
        }
    }
    
    /**
     * Validates if a private key corresponds to the given REV address, throwing exception on failure
     * @param privateKey The private key to validate
     * @param expectedRevAddress The expected REV address
     * @throws InvalidPrivateKeyException if the private key doesn't match the expected REV address
     */
    public static void validatePrivateKeyForRevAddressOrThrow(byte[] privateKey, String expectedRevAddress) 
            throws InvalidPrivateKeyException {
        if (!validatePrivateKeyForRevAddress(privateKey, expectedRevAddress)) {
            throw new InvalidPrivateKeyException(
                "Private key does not correspond to expected REV address: " + expectedRevAddress);
        }
    }
    
    /**
     * Derives an ETH address from a public key
     * @param publicKey The public key
     * @return The ETH address (without 0x prefix)
     */
    private static String deriveEthAddressFromPublicKey(byte[] publicKey) {
        try {
            // Skip the first byte of the public key
            byte[] pubKeySkipFirst = new byte[publicKey.length - 1];
            System.arraycopy(publicKey, 1, pubKeySkipFirst, 0, pubKeySkipFirst.length);
            
            // Hash the public key using Keccak-256
            MessageDigest digest = MessageDigest.getInstance("KECCAK-256", "BC");
            byte[] hashedPubKey = digest.digest(pubKeySkipFirst);
            
            // Take the last 20 bytes (40 hex chars) of the hash as ETH address
            byte[] ethAddressBytes = new byte[20];
            System.arraycopy(hashedPubKey, hashedPubKey.length - 20, ethAddressBytes, 0, 20);
            
            // Convert to hex string
            return Hex.encode(ethAddressBytes);
        } catch (Exception e) {
            throw new RuntimeException("Failed to derive ETH address from public key", e);
        }
    }
    
    /**
     * Derives a REV address from an ETH address
     * @param ethAddress The ETH address (without 0x prefix)
     * @return The REV address
     */
    private static String deriveRevAddressFromEthAddress(String ethAddress) {
        try {
            // Hash ETH address
            byte[] ethAddressBytes = Hex.decode(ethAddress);
            MessageDigest digest = MessageDigest.getInstance("KECCAK-256", "BC");
            byte[] ethHash = digest.digest(ethAddressBytes);
            String ethHashHex = Hex.encode(ethHash);
            
            // Add prefix with hash
            String payload = COIN_ID + VERSION + ethHashHex;
            byte[] payloadBytes = Hex.decode(payload);
            
            // Calculate checksum (blake2b-256 hash)
            MessageDigest blake2b = MessageDigest.getInstance(Blake2b.BLAKE2_B_256);
            blake2b.update(payloadBytes);
            byte[] checksumBytes = blake2b.digest();
            String checksum = Hex.encode(checksumBytes).substring(0, 8);
            
            // Return REV address (payload + checksum encoded as Base58)
            String fullHex = payload + checksum;
            byte[] fullBytes = Hex.decode(fullHex);
            return encodeBase58(fullBytes);
        } catch (Exception e) {
            throw new RuntimeException("Failed to derive REV address from ETH address", e);
        }
    }
    
    /**
     * Encodes a byte array to Base58 string
     */
    private static String encodeBase58(byte[] input) {
        // Count leading zeros
        int zeros = 0;
        while (zeros < input.length && input[zeros] == 0) {
            zeros++;
        }
        
        // Convert to BigInteger
        byte[] buffer = Arrays.copyOf(input, input.length);
        BigInteger value = new BigInteger(1, buffer);
        
        // Convert to Base58
        StringBuilder sb = new StringBuilder();
        while (value.compareTo(BigInteger.ZERO) > 0) {
            BigInteger[] divmod = value.divideAndRemainder(BASE);
            value = divmod[0];
            sb.append(ALPHABET.charAt(divmod[1].intValue()));
        }
        
        // Add leading '1's for each leading zero byte
        for (int i = 0; i < zeros; i++) {
            sb.append(ALPHABET.charAt(0));
        }
        
        // Reverse the string
        return sb.reverse().toString();
    }
    
    /**
     * Convenience method to validate private key as hex string
     * @param privateKeyHex The private key as hex string
     * @param expectedRevAddress The expected REV address
     * @return true if valid, false otherwise
     */
    public static boolean validatePrivateKeyForRevAddress(String privateKeyHex, String expectedRevAddress) {
        try {
            byte[] privateKey = Hex.decode(privateKeyHex);
            return validatePrivateKeyForRevAddress(privateKey, expectedRevAddress);
        } catch (Exception e) {
            LOGGER.warn("Failed to decode private key hex: {}", privateKeyHex, e);
            return false;
        }
    }
    
    /**
     * Convenience method to validate private key as hex string, throwing exception on failure
     * @param privateKeyHex The private key as hex string
     * @param expectedRevAddress The expected REV address
     * @throws InvalidPrivateKeyException if the private key doesn't match the expected REV address or is invalid
     */
    public static void validatePrivateKeyForRevAddressOrThrow(String privateKeyHex, String expectedRevAddress) 
            throws InvalidPrivateKeyException {
        try {
            byte[] privateKey = Hex.decode(privateKeyHex);
            validatePrivateKeyForRevAddressOrThrow(privateKey, expectedRevAddress);
        } catch (IllegalArgumentException e) {
            throw new InvalidPrivateKeyException("Invalid private key format: " + e.getMessage(), e);
        }
    }
    
    /**
     * Derives a REV address from a private key
     * @param privateKey The private key
     * @return The derived REV address
     */
    public static String deriveRevAddressFromPrivateKey(byte[] privateKey) {
        try {
            final Secp256k1 secp256k1 = Secp256k1.get();
            byte[] pubKey = secp256k1.pubkeyCreate(privateKey);
            String ethAddress = deriveEthAddressFromPublicKey(pubKey);
            return deriveRevAddressFromEthAddress(ethAddress);
        } catch (Exception e) {
            throw new RuntimeException("Failed to derive REV address from private key", e);
        }
    }
    
    /**
     * Derives a REV address from a private key hex string
     * @param privateKeyHex The private key as hex string
     * @return The derived REV address
     */
    public static String deriveRevAddressFromPrivateKey(String privateKeyHex) {
        try {
            // Pad the private key with leading zeros to ensure it's 32 bytes (64 hex chars)
            String paddedKey = privateKeyHex;
            if (privateKeyHex.length() < 64) {
                paddedKey = String.format("%064x", new BigInteger(privateKeyHex, 16));
            }
            
            byte[] privateKey = Hex.decode(paddedKey);
            return deriveRevAddressFromPrivateKey(privateKey);
        } catch (IllegalArgumentException e) {
            throw new InvalidPrivateKeyException("Invalid private key format: " + e.getMessage(), e);
        }
    }
} 