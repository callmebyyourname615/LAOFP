package com.example.switching.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import com.fasterxml.jackson.databind.ObjectMapper;

public final class RequestHashUtil {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private RequestHashUtil() {
    }

    public static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedHash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return toHex(encodedHash);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to hash input", ex);
        }
    }

    public static String sha256(Object payload) {
        try {
            return sha256(OBJECT_MAPPER.writeValueAsString(payload));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to hash request payload", ex);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format("%02x", value));
        }
        return builder.toString();
    }
}