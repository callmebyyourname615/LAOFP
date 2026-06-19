package com.example.switching.webhook.crypto;

/**
 * Raised when encryption, decryption, or the backing KMS operation fails.
 * Callers must fail closed and must never fall back to plaintext.
 */
public class SecretEncryptionException extends RuntimeException {

    public SecretEncryptionException(String message) {
        super(message);
    }

    public SecretEncryptionException(String message, Throwable cause) {
        super(message, cause);
    }
}
