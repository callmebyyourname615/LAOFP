package com.example.switching.webhook.crypto;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Vault Transit implementation used for production key wrapping. */
public final class VaultTransitKeyEncryptionService implements KeyEncryptionService {

    private static final Pattern SAFE_PATH_SEGMENT = Pattern.compile("[A-Za-z0-9._-]+");

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final URI encryptUri;
    private final URI decryptUri;
    private final VaultTokenProvider tokenProvider;
    private final String namespace;
    private final String keyId;
    private final java.time.Duration requestTimeout;

    public VaultTransitKeyEncryptionService(HttpClient httpClient,
                                            ObjectMapper objectMapper,
                                            WebhookEncryptionProperties properties,
                                            VaultTokenProvider tokenProvider) {
        WebhookEncryptionProperties.Vault vault = properties.getVault();
        requireText(vault.getAddress(), "Vault address");
        requireSafeSegment(vault.getMount(), "Vault transit mount");
        requireSafeSegment(vault.getKey(), "Vault transit key");

        String address = vault.getAddress().replaceAll("/+$", "");
        if (!address.startsWith("https://") && !address.startsWith("http://")) {
            throw new IllegalStateException("Vault address must be an HTTP(S) URI");
        }
        this.encryptUri = URI.create(address + "/v1/" + vault.getMount() + "/encrypt/" + vault.getKey());
        this.decryptUri = URI.create(address + "/v1/" + vault.getMount() + "/decrypt/" + vault.getKey());
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.tokenProvider = tokenProvider;
        this.namespace = vault.getNamespace();
        this.keyId = "vault-transit:" + vault.getMount() + "/" + vault.getKey();
        this.requestTimeout = properties.getRequestTimeout();
    }

    @Override
    public WrappedKey wrapKey(byte[] plaintextKey) {
        String plaintext = Base64.getEncoder().encodeToString(plaintextKey);
        JsonNode response = post(encryptUri, Map.of("plaintext", plaintext));
        String ciphertext = requiredText(response.path("data").path("ciphertext"), "data.ciphertext");
        return new WrappedKey(ciphertext, keyId);
    }

    @Override
    public byte[] unwrapKey(String wrappedKeyCiphertext, String requestedKeyId) {
        if (!keyId.equals(requestedKeyId)) {
            throw new SecretEncryptionException("Webhook envelope references an unexpected Vault key id");
        }
        JsonNode response = post(decryptUri, Map.of("ciphertext", wrappedKeyCiphertext));
        String plaintext = requiredText(response.path("data").path("plaintext"), "data.plaintext");
        try {
            return Base64.getDecoder().decode(plaintext);
        } catch (IllegalArgumentException error) {
            throw new SecretEncryptionException("Vault returned invalid Base64 plaintext", error);
        }
    }

    private JsonNode post(URI uri, Map<String, String> body) {
        VaultResponse response = postOnce(uri, body, tokenProvider.token());
        if (response.statusCode() == 401 || response.statusCode() == 403) {
            tokenProvider.invalidate();
            response = postOnce(uri, body, tokenProvider.token());
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new SecretEncryptionException(
                    "Vault Transit request failed with HTTP " + response.statusCode());
        }
        try {
            return objectMapper.readTree(response.body());
        } catch (Exception error) {
            throw new SecretEncryptionException("Vault Transit returned invalid JSON", error);
        }
    }

    private VaultResponse postOnce(URI uri, Map<String, String> body, String token) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(requestTimeout)
                    .header("Content-Type", "application/json")
                    .header("X-Vault-Token", token)
                    .POST(HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(body), StandardCharsets.UTF_8));
            if (namespace != null && !namespace.isBlank()) {
                builder.header("X-Vault-Namespace", namespace);
            }
            HttpResponse<String> response = httpClient.send(
                    builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new VaultResponse(response.statusCode(), response.body());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new SecretEncryptionException("Vault Transit request interrupted", interrupted);
        } catch (Exception error) {
            throw new SecretEncryptionException("Vault Transit request failed", error);
        }
    }

    private static String requiredText(JsonNode node, String field) {
        if (node == null || !node.isTextual() || node.textValue().isBlank()) {
            throw new SecretEncryptionException("Vault Transit response is missing " + field);
        }
        return node.textValue();
    }

    private static void requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(label + " is required");
        }
    }

    private static void requireSafeSegment(String value, String label) {
        requireText(value, label);
        if (!SAFE_PATH_SEGMENT.matcher(value).matches()) {
            throw new IllegalStateException(label + " contains unsupported characters");
        }
    }

    private record VaultResponse(int statusCode, String body) {
    }
}
