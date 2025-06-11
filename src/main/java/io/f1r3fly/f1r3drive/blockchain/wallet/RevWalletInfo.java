package io.f1r3fly.f1r3drive.blockchain.wallet;

import javax.annotation.Nullable;

import org.checkerframework.checker.nullness.qual.NonNull;

/**
 * Represents REV wallet information containing the address and signing key.
 * This record encapsulates the essential credentials needed for blockchain operations.
 */
public record RevWalletInfo(@NonNull String revAddress, @Nullable byte[] signingKey) {
    
    /**
     * Validates that the wallet info is not null and contains valid data.
     */
    public RevWalletInfo {
        if (revAddress.trim().isEmpty()) {
            throw new IllegalArgumentException("REV address cannot be empty");
        }
        if (signingKey != null && signingKey.length == 0) {
            throw new IllegalArgumentException("Signing key cannot be empty");
        }
    }
} 