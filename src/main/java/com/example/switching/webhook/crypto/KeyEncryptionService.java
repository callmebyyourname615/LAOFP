package com.example.switching.webhook.crypto;

/**
 * Provider abstraction for wrapping and unwrapping short-lived data-encryption keys.
 * Implementations may use Vault Transit, a cloud KMS, or a non-production local key.
 */
public interface KeyEncryptionService {

    WrappedKey wrapKey(byte[] plaintextKey);

    byte[] unwrapKey(String wrappedKeyCiphertext, String keyId);
}
