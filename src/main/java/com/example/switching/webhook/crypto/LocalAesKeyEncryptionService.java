package com.example.switching.webhook.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Non-production key wrapper for local development and tests.
 * Production startup rejects this provider and requires Vault Transit.
 */
public final class LocalAesKeyEncryptionService implements KeyEncryptionService {

    private static final String KEY_ID = "local-aes:v1";
    private static final String PREFIX = "local:v1:";
    private static final int NONCE_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final byte[] AAD = "switching:webhook-dek:v1".getBytes(StandardCharsets.UTF_8);

    private final SecretKeySpec masterKey;
    private final SecureRandom secureRandom;

    public LocalAesKeyEncryptionService(String masterKeyBase64, SecureRandom secureRandom) {
        if (masterKeyBase64 == null || masterKeyBase64.isBlank()) {
            throw new IllegalStateException(
                    "switching.webhook.encryption.local.master-key-base64 is required for local provider");
        }
        byte[] key;
        try {
            key = Base64.getDecoder().decode(masterKeyBase64);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Local webhook master key must be valid Base64", ex);
        }
        if (key.length != 32) {
            throw new IllegalStateException("Local webhook master key must decode to exactly 32 bytes");
        }
        this.masterKey = new SecretKeySpec(key, "AES");
        this.secureRandom = secureRandom;
    }

    @Override
    public WrappedKey wrapKey(byte[] plaintextKey) {
        byte[] nonce = new byte[NONCE_BYTES];
        secureRandom.nextBytes(nonce);
        try {
            byte[] encrypted = crypt(Cipher.ENCRYPT_MODE, plaintextKey, nonce);
            String encoded = PREFIX
                    + Base64.getUrlEncoder().withoutPadding().encodeToString(nonce)
                    + ":"
                    + Base64.getUrlEncoder().withoutPadding().encodeToString(encrypted);
            return new WrappedKey(encoded, KEY_ID);
        } catch (GeneralSecurityException ex) {
            throw new SecretEncryptionException("Unable to wrap webhook data-encryption key", ex);
        }
    }

    @Override
    public byte[] unwrapKey(String wrappedKeyCiphertext, String keyId) {
        if (!KEY_ID.equals(keyId)) {
            throw new SecretEncryptionException("Unknown local key id");
        }
        if (wrappedKeyCiphertext == null || !wrappedKeyCiphertext.startsWith(PREFIX)) {
            throw new SecretEncryptionException("Invalid local wrapped key format");
        }
        String[] parts = wrappedKeyCiphertext.substring(PREFIX.length()).split(":", 2);
        if (parts.length != 2) {
            throw new SecretEncryptionException("Invalid local wrapped key payload");
        }
        try {
            return crypt(
                    Cipher.DECRYPT_MODE,
                    Base64.getUrlDecoder().decode(parts[1]),
                    Base64.getUrlDecoder().decode(parts[0]));
        } catch (Exception ex) {
            throw new SecretEncryptionException("Unable to unwrap webhook data-encryption key", ex);
        }
    }

    private byte[] crypt(int mode, byte[] input, byte[] nonce) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, masterKey, new GCMParameterSpec(GCM_TAG_BITS, nonce));
        cipher.updateAAD(AAD);
        return cipher.doFinal(input);
    }
}
