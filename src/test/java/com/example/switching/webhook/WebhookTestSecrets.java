package com.example.switching.webhook;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

import com.example.switching.webhook.crypto.EncryptedSecret;
import com.example.switching.webhook.crypto.EnvelopeSecretEncryptionService;
import com.example.switching.webhook.crypto.LocalAesKeyEncryptionService;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Test fixture for seeding webhook registrations after plaintext secret storage was removed.
 */
public final class WebhookTestSecrets {

    public static final String PLAINTEXT = "test-webhook-secret-with-at-least-32-characters";
    private static final String MASTER_KEY_BASE64 =
            "NImwCmFwkSIeDgy8UJtzGq86A389puEEe6gi2Wdo9MM=";

    private WebhookTestSecrets() {
    }

    public static EncryptedSecret encrypted() {
        SecureRandom random = new SecureRandom();
        var keyEncryptionService = new LocalAesKeyEncryptionService(MASTER_KEY_BASE64, random);
        var encryptionService = new EnvelopeSecretEncryptionService(
                keyEncryptionService,
                new ObjectMapper(),
                random);
        return encryptionService.encrypt(PLAINTEXT);
    }

    public static String sha256() {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(PLAINTEXT.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
