package com.example.switching.reportdelivery;

import java.util.Map;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class ReportDestinationResolver {

    private final Environment environment;

    public ReportDestinationResolver(Environment environment) {
        this.environment = environment;
    }

    public String required(Map<String, Object> config, String key) {
        Object raw = config.get(key);
        if (raw == null || String.valueOf(raw).isBlank()) {
            throw new IllegalArgumentException("Missing destination field " + key);
        }
        return String.valueOf(raw).trim();
    }

    public String secretReference(Map<String, Object> config, String key) {
        String reference = required(config, key);
        String variable = validateSecretReference(reference, key);
        String value = environment.getProperty(variable);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Required report-delivery secret is unavailable: " + variable);
        }
        return value;
    }

    public void validateConfiguration(DeliveryChannel channel, Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            throw new IllegalArgumentException("Destination configuration is required");
        }
        switch (channel) {
            case SFTP -> {
                required(config, "host");
                required(config, "username");
                required(config, "path");
                required(config, "knownHostsPath");
                validateSecretReference(required(config, "privateKeyPath"), "privateKeyPath");
                validatePort(config.getOrDefault("port", 22));
            }
            case S3 -> {
                required(config, "endpoint");
                required(config, "bucket");
                validateSecretReference(required(config, "accessKey"), "accessKey");
                validateSecretReference(required(config, "secretKey"), "secretKey");
            }
            case EMAIL_LINK -> required(config, "recipientReference");
        }
        rejectSecretLikePlaintext(config);
    }

    private static String validateSecretReference(String reference, String key) {
        if (!reference.startsWith("env:")) {
            throw new IllegalArgumentException(
                    key + " must be an env:NAME reference, not plaintext");
        }
        String variable = reference.substring(4);
        if (!variable.matches("[A-Z][A-Z0-9_]{2,127}")) {
            throw new IllegalArgumentException("Invalid environment secret reference");
        }
        return variable;
    }

    private static void validatePort(Object rawPort) {
        int port;
        try {
            port = Integer.parseInt(String.valueOf(rawPort));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid destination port", exception);
        }
        if (port < 1 || port > 65_535) {
            throw new IllegalArgumentException("Invalid destination port");
        }
    }

    private static void rejectSecretLikePlaintext(Map<String, Object> config) {
        for (Map.Entry<String, Object> entry : config.entrySet()) {
            String key = entry.getKey().toLowerCase(java.util.Locale.ROOT);
            if ((key.contains("password") || key.contains("privatekey")
                    || key.contains("secret") || key.equals("accesskey"))
                    && entry.getValue() != null
                    && !String.valueOf(entry.getValue()).startsWith("env:")) {
                throw new IllegalArgumentException(
                        "Secret-like destination values must be environment references");
            }
        }
    }
}
