package com.example.switching.crossborder.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

@Component
public class RailSignatureVerifier {

    private static final int MAX_PAYLOAD_BYTES = 2 * 1024 * 1024;

    public void verifyHmacSha256(
            String payload,
            String signature,
            String secret) {
        if (secret == null
                || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "Rail HMAC secret must contain at least 32 bytes");
        }
        if (payload == null
                || payload.getBytes(StandardCharsets.UTF_8).length > MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("Rail payload is invalid or too large");
        }
        if (signature == null || !signature.matches("(?i)^[0-9a-f]{64}$")) {
            throw new IllegalArgumentException("Malformed rail signature");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"));
            byte[] expected = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            byte[] actual = HexFormat.of().parseHex(signature);
            if (!MessageDigest.isEqual(expected, actual)) {
                throw new SecurityException("Invalid rail payload signature");
            }
        } catch (SecurityException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to verify rail signature", exception);
        }
    }
}
