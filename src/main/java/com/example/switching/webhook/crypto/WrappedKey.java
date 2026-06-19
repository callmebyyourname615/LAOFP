package com.example.switching.webhook.crypto;

/** Wrapped data-encryption key returned by a KMS provider. */
public record WrappedKey(String ciphertext, String keyId) {

    public WrappedKey {
        if (ciphertext == null || ciphertext.isBlank()) {
            throw new IllegalArgumentException("wrapped key ciphertext is required");
        }
        if (keyId == null || keyId.isBlank()) {
            throw new IllegalArgumentException("wrapped key id is required");
        }
    }
}
