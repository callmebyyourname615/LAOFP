package com.example.switching.webhook.crypto;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Exchanges a short-lived projected service-account JWT for a short-lived Vault token. */
final class KubernetesVaultTokenProvider implements VaultTokenProvider {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final URI loginUri;
    private final String namespace;
    private final String role;
    private final Path jwtPath;
    private final java.time.Duration requestTimeout;
    private final java.time.Duration renewalSkew;

    private volatile String cachedToken;
    private volatile Instant refreshAt = Instant.EPOCH;

    KubernetesVaultTokenProvider(HttpClient httpClient,
                                 ObjectMapper objectMapper,
                                 WebhookEncryptionProperties properties) {
        WebhookEncryptionProperties.Vault vault = properties.getVault();
        String address = required(vault.getAddress(), "Vault address").replaceAll("/+$", "");
        String authMount = safeSegment(vault.getKubernetesAuthMount(), "Vault Kubernetes auth mount");
        this.loginUri = URI.create(address + "/v1/auth/" + authMount + "/login");
        this.namespace = vault.getNamespace();
        this.role = required(vault.getKubernetesRole(), "Vault Kubernetes role");
        this.jwtPath = Path.of(required(vault.getServiceAccountTokenFile(), "Vault service-account token file"));
        this.requestTimeout = properties.getRequestTimeout();
        this.renewalSkew = vault.getTokenRenewalSkew();
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String token() {
        String current = cachedToken;
        if (current != null && Instant.now().isBefore(refreshAt)) {
            return current;
        }
        synchronized (this) {
            if (cachedToken == null || !Instant.now().isBefore(refreshAt)) {
                login();
            }
            return cachedToken;
        }
    }

    @Override
    public synchronized void invalidate() {
        cachedToken = null;
        refreshAt = Instant.EPOCH;
    }

    private void login() {
        try {
            String jwt = Files.readString(jwtPath, StandardCharsets.UTF_8).trim();
            if (jwt.isBlank()) {
                throw new SecretEncryptionException("Projected Vault service-account token is blank");
            }
            String body = objectMapper.writeValueAsString(Map.of("role", role, "jwt", jwt));
            HttpRequest.Builder request = HttpRequest.newBuilder(loginUri)
                    .timeout(requestTimeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
            if (namespace != null && !namespace.isBlank()) {
                request.header("X-Vault-Namespace", namespace);
            }
            HttpResponse<String> response = httpClient.send(
                    request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new SecretEncryptionException(
                        "Vault Kubernetes login failed with HTTP " + response.statusCode());
            }
            JsonNode auth = objectMapper.readTree(response.body()).path("auth");
            String token = text(auth.path("client_token"), "auth.client_token");
            long leaseSeconds = auth.path("lease_duration").asLong(0);
            if (leaseSeconds <= 0) {
                throw new SecretEncryptionException("Vault Kubernetes login returned no lease_duration");
            }
            long usableSeconds = Math.max(1, leaseSeconds - renewalSkew.toSeconds());
            cachedToken = token;
            refreshAt = Instant.now().plusSeconds(usableSeconds);
        } catch (SecretEncryptionException error) {
            throw error;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new SecretEncryptionException("Vault Kubernetes login interrupted", interrupted);
        } catch (Exception error) {
            throw new SecretEncryptionException("Vault Kubernetes login failed", error);
        }
    }

    private static String text(JsonNode node, String field) {
        if (!node.isTextual() || node.asText().isBlank()) {
            throw new SecretEncryptionException("Vault login response is missing " + field);
        }
        return node.asText();
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(field + " is required");
        }
        return value.trim();
    }

    private static String safeSegment(String value, String field) {
        String required = required(value, field);
        if (!required.matches("[A-Za-z0-9._/-]+")) {
            throw new IllegalStateException(field + " contains unsupported characters");
        }
        return required.replaceAll("^/+|/+$", "");
    }
}
