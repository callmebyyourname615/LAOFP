package com.example.switching.security.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class SensitiveDataSanitizerTest {
    private final SensitiveDataSanitizer sanitizer = new SensitiveDataSanitizer(new ObjectMapper());

    @Test
    void masksNestedSensitiveFieldsAndArrays() {
        String value = sanitizer.sanitizeJson("""
                {"account":"1234","client_secret":"top-secret",\
                 "nested":{"authorization":"Bearer abc"},\
                 "items":[{"access-token":"token-value"}]}""");
        assertFalse(value.contains("top-secret"));
        assertFalse(value.contains("Bearer abc"));
        assertFalse(value.contains("token-value"));
        assertTrue(value.contains("***"));
    }

    @Test
    void masksSecretsInMalformedNonJsonText() {
        String value = sanitizer.sanitizeJson(
                "request failed password=hunter2 Authorization: Bearer abc.def api_key=my-key");
        assertFalse(value.contains("hunter2"));
        assertFalse(value.contains("abc.def"));
        assertFalse(value.contains("my-key"));
    }
}
