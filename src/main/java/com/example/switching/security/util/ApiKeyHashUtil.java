package com.example.switching.security.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

public final class ApiKeyHashUtil {

    private static final int KEY_BYTES = 32;
    private static final String PREFIX = "sk-";

    private ApiKeyHashUtil() {}

    /**
     * Computes SHA-256 hex digest of the given raw key string.
     * This is what is stored in api_keys.key_value.
     */
    public static String hash(String rawKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(rawKey.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /**
     * Generates a cryptographically random API key.
     * Format: sk-{64 hex chars}
     * Returns the plaintext key — caller must store the hash, not this value.
     */
    public static String generate() {
        SecureRandom rng = new SecureRandom();
        byte[] bytes = new byte[KEY_BYTES];
        rng.nextBytes(bytes);
        return PREFIX + HexFormat.of().formatHex(bytes);
    }

    /**
     * Returns the display prefix (first 12 chars) of a raw key.
     * Stored in key_prefix so operators can identify a key without seeing it in full.
     */
    public static String prefix(String rawKey) {
        return rawKey.length() > 12 ? rawKey.substring(0, 12) : rawKey;
    }
}
