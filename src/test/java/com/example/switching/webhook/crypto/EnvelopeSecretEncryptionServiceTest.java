package com.example.switching.webhook.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.SecureRandom;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class EnvelopeSecretEncryptionServiceTest {

    private static final String MASTER_KEY =
            "NImwCmFwkSIeDgy8UJtzGq86A389puEEe6gi2Wdo9MM=";

    private SecretEncryptionService service;

    @BeforeEach
    void setUp() {
        SecureRandom secureRandom = new SecureRandom();
        service = new EnvelopeSecretEncryptionService(
                new LocalAesKeyEncryptionService(MASTER_KEY, secureRandom),
                new ObjectMapper(),
                secureRandom);
    }

    @Test
    void encryptAndDecryptRoundTripWithoutPlaintextLeak() {
        String plaintext = "a-strong-webhook-secret-that-is-never-persisted";

        EncryptedSecret encrypted = service.encrypt(plaintext);

        assertTrue(encrypted.ciphertext().startsWith("env:v1:"));
        assertEquals("local-aes:v1", encrypted.keyId());
        assertEquals(1, encrypted.version());
        assertNotEquals(plaintext, encrypted.ciphertext());
        assertTrue(!encrypted.ciphertext().contains(plaintext));
        assertEquals(plaintext, service.decrypt(encrypted.ciphertext()));
    }

    @Test
    void samePlaintextProducesDifferentCiphertextBecauseDekAndNonceAreRandom() {
        String plaintext = "same-secret-value-with-at-least-32-characters";
        assertNotEquals(
                service.encrypt(plaintext).ciphertext(),
                service.encrypt(plaintext).ciphertext());
    }

    @Test
    void tamperedEnvelopeFailsClosed() {
        String ciphertext = service.encrypt("secret-value-with-at-least-32-characters").ciphertext();
        String tampered = ciphertext.substring(0, ciphertext.length() - 1)
                + (ciphertext.endsWith("A") ? "B" : "A");

        assertThrows(SecretEncryptionException.class, () -> service.decrypt(tampered));
    }
}
