package io.f1r3fly.f1r3drive.blockchain.wallet;

/**
 * Represents REV wallet information containing the address and signing key.
 * This record encapsulates the essential credentials needed for blockchain operations.
 */
public record RevWalletInfo(String revAddress, byte[] signingKey) {
    
    /**
     * Validates that the wallet info is not null and contains valid data.
     */
    public RevWalletInfo {
        if (revAddress == null || revAddress.trim().isEmpty()) {
            throw new IllegalArgumentException("REV address cannot be null or empty");
        }
        if (signingKey == null || signingKey.length == 0) {
            throw new IllegalArgumentException("Signing key cannot be null or empty");
        }
    }
} 