package com.example.switching.webhook.crypto;

/** Encrypts and decrypts webhook signing secrets using envelope encryption. */
public interface SecretEncryptionService {

    EncryptedSecret encrypt(String plaintext);

    String decrypt(String ciphertext);
}
