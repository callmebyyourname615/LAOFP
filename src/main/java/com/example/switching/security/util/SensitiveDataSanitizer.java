package com.example.switching.security.util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Masks secret-bearing fields before payloads enter logs or the audit chain. */
@Component
public class SensitiveDataSanitizer {
    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "password", "passphrase", "secret", "signingsecret", "clientsecret",
            "authorization", "token", "accesstoken", "refreshtoken", "apikey",
            "secretciphertext", "previoussecretciphertext", "privatekey", "cvv", "pin");
    private static final Pattern BEARER = Pattern.compile(
            "(?i)bearer\\s+[a-z0-9._~+/=-]+");
    private static final Pattern KEY_VALUE_SECRET = Pattern.compile(
            "(?i)(password|passphrase|secret|client[_-]?secret|api[_-]?key|access[_-]?token|refresh[_-]?token)"
                    + "(\\s*[=:]\\s*)([^,;\\s&]+)");

    private final ObjectMapper mapper;

    public SensitiveDataSanitizer(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String sanitizeJson(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        try {
            JsonNode node = mapper.readTree(value);
            sanitize(node);
            return mapper.writeValueAsString(node);
        } catch (Exception ignored) {
            String bearerMasked = BEARER.matcher(value).replaceAll("Bearer ***");
            return KEY_VALUE_SECRET.matcher(bearerMasked).replaceAll("$1$2***");
        }
    }

    private void sanitize(JsonNode node) {
        if (node instanceof ObjectNode object) {
            // Snapshot names first so replacing a value never mutates an active iterator.
            List<String> fieldNames = new ArrayList<>();
            Iterator<Map.Entry<String, JsonNode>> fields = object.fields();
            while (fields.hasNext()) {
                fieldNames.add(fields.next().getKey());
            }
            for (String fieldName : fieldNames) {
                JsonNode fieldValue = object.get(fieldName);
                if (SENSITIVE_KEYS.contains(normalize(fieldName))) {
                    object.put(fieldName, "***");
                } else if (fieldValue != null) {
                    sanitize(fieldValue);
                }
            }
        } else if (node instanceof ArrayNode array) {
            array.forEach(this::sanitize);
        }
    }

    private String normalize(String key) {
        return key.replace("-", "")
                .replace("_", "")
                .replace(".", "")
                .toLowerCase(Locale.ROOT);
    }
}
