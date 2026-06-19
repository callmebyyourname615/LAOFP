package com.example.switching.webhook.crypto;

/**
 * Result of encrypting a sensitive webhook signing secret.
 *
 * @param ciphertext self-contained envelope ciphertext safe to persist
 * @param keyId logical KMS/Vault key reference used to wrap the data-encryption key
 * @param version envelope format version
 */
public record EncryptedSecret(String ciphertext, String keyId, int version) {

    public EncryptedSecret {
        if (ciphertext == null || ciphertext.isBlank()) {
            throw new IllegalArgumentException("ciphertext is required");
        }
        if (keyId == null || keyId.isBlank()) {
            throw new IllegalArgumentException("keyId is required");
        }
        if (version < 1) {
            throw new IllegalArgumentException("version must be positive");
        }
    }
}
