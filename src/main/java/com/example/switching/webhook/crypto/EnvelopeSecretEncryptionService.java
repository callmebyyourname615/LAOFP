package com.example.switching.webhook.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * AES-256-GCM envelope encryption.
 *
 * <p>A fresh data-encryption key (DEK) is generated for every secret. The DEK is
 * wrapped by {@link KeyEncryptionService}; only the wrapped DEK and AES-GCM
 * ciphertext are persisted. The encoded envelope is self-contained so an older
 * rotated secret can still be decrypted without an additional database key-id
 * column.</p>
 */
public final class EnvelopeSecretEncryptionService implements SecretEncryptionService {

    private static final int FORMAT_VERSION = 1;
    private static final int DEK_BYTES = 32;
    private static final int NONCE_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final byte[] AAD = "switching:webhook-secret:v1".getBytes(StandardCharsets.UTF_8);
    private static final String PREFIX = "env:v1:";

    private final KeyEncryptionService keyEncryptionService;
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom;

    public EnvelopeSecretEncryptionService(KeyEncryptionService keyEncryptionService,
                                           ObjectMapper objectMapper,
                                           SecureRandom secureRandom) {
        this.keyEncryptionService = keyEncryptionService;
        this.objectMapper = objectMapper;
        this.secureRandom = secureRandom;
    }

    @Override
    public EncryptedSecret encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            throw new IllegalArgumentException("plaintext secret is required");
        }

        byte[] dek = new byte[DEK_BYTES];
        byte[] nonce = new byte[NONCE_BYTES];
        secureRandom.nextBytes(dek);
        secureRandom.nextBytes(nonce);

        try {
            byte[] encrypted = crypt(
                    Cipher.ENCRYPT_MODE,
                    plaintext.getBytes(StandardCharsets.UTF_8),
                    dek,
                    nonce);
            WrappedKey wrappedKey = keyEncryptionService.wrapKey(dek);

            EnvelopePayload payload = new EnvelopePayload(
                    FORMAT_VERSION,
                    wrappedKey.keyId(),
                    wrappedKey.ciphertext(),
                    Base64.getEncoder().encodeToString(nonce),
                    Base64.getEncoder().encodeToString(encrypted));

            String encoded = PREFIX + Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(objectMapper.writeValueAsBytes(payload));
            return new EncryptedSecret(encoded, wrappedKey.keyId(), FORMAT_VERSION);
        } catch (GeneralSecurityException | JsonProcessingException ex) {
            throw new SecretEncryptionException("Unable to encrypt webhook secret", ex);
        } finally {
            Arrays.fill(dek, (byte) 0);
        }
    }

    @Override
    public String decrypt(String ciphertext) {
        if (ciphertext == null || !ciphertext.startsWith(PREFIX)) {
            throw new SecretEncryptionException("Unsupported or missing webhook secret envelope");
        }

        byte[] dek = null;
        try {
            byte[] serialized = Base64.getUrlDecoder().decode(ciphertext.substring(PREFIX.length()));
            EnvelopePayload payload = objectMapper.readValue(serialized, EnvelopePayload.class);
            if (payload.version() != FORMAT_VERSION) {
                throw new SecretEncryptionException(
                        "Unsupported webhook secret envelope version: " + payload.version());
            }

            dek = keyEncryptionService.unwrapKey(payload.wrappedKey(), payload.keyId());
            if (dek.length != DEK_BYTES) {
                throw new SecretEncryptionException("KMS returned an invalid data-encryption key length");
            }

            byte[] plain = crypt(
                    Cipher.DECRYPT_MODE,
                    Base64.getDecoder().decode(payload.ciphertext()),
                    dek,
                    Base64.getDecoder().decode(payload.nonce()));
            return new String(plain, StandardCharsets.UTF_8);
        } catch (SecretEncryptionException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new SecretEncryptionException("Unable to decrypt webhook secret", ex);
        } finally {
            if (dek != null) {
                Arrays.fill(dek, (byte) 0);
            }
        }
    }

    private byte[] crypt(int mode, byte[] input, byte[] key, byte[] nonce)
            throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(GCM_TAG_BITS, nonce));
        cipher.updateAAD(AAD);
        return cipher.doFinal(input);
    }

    private record EnvelopePayload(
            int version,
            String keyId,
            String wrappedKey,
            String nonce,
            String ciphertext) {
    }
}
