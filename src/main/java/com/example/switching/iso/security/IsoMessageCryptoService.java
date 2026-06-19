package com.example.switching.iso.security;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.example.switching.iso.exception.IsoMessageCryptoException;

@Service
public class IsoMessageCryptoService {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;

    private static final String DEV_FALLBACK_KEY = "0123456789abcdef0123456789abcdef";

    private final SecureRandom secureRandom = new SecureRandom();
    private final SecretKeySpec secretKeySpec;

    public IsoMessageCryptoService(
            @Value("${switching.security.message-crypto-key-base64:}") String base64Key,
            Environment environment) {
        this.secretKeySpec = new SecretKeySpec(resolveKey(base64Key, environment), ALGORITHM);
    }

    public String encrypt(String plainText) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    secretKeySpec,
                    new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
            );

            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            byte[] output = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, output, 0, iv.length);
            System.arraycopy(cipherText, 0, output, iv.length, cipherText.length);

            return Base64.getEncoder().encodeToString(output);
        } catch (Exception ex) {
            throw new IsoMessageCryptoException("Failed to encrypt ISO message", ex);
        }
    }

    public String decrypt(String encryptedPayload) {
        try {
            byte[] input = Base64.getDecoder().decode(encryptedPayload);

            byte[] iv = Arrays.copyOfRange(input, 0, GCM_IV_LENGTH_BYTES);
            byte[] cipherText = Arrays.copyOfRange(input, GCM_IV_LENGTH_BYTES, input.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    secretKeySpec,
                    new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
            );

            byte[] plainText = cipher.doFinal(cipherText);
            return new String(plainText, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IsoMessageCryptoException("Failed to decrypt ISO message", ex);
        }
    }

    private byte[] resolveKey(String base64Key, Environment environment) {
        if (StringUtils.hasText(base64Key)) {
            byte[] decoded = Base64.getDecoder().decode(base64Key);

            if (decoded.length == 16 || decoded.length == 24 || decoded.length == 32) {
                return decoded;
            }

            throw new IllegalArgumentException(
                    "Invalid message crypto key length. AES key must be 16, 24, or 32 bytes."
            );
        }

        boolean isTestProfile = java.util.Arrays.asList(environment.getActiveProfiles()).contains("test");

        if (!isTestProfile) {
            throw new IllegalStateException(
                    "switching.security.message-crypto-key-base64 must be set. "
                    + "Running without a crypto key is not allowed outside of the test profile."
            );
        }

        return DEV_FALLBACK_KEY.getBytes(StandardCharsets.UTF_8);
    }
}