package com.example.switching.crossborder.service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class OAuth2ClientCredentialsTokenProvider {
    private final Environment environment;
    private final ObjectMapper mapper;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final Map<String, CachedToken> cache = new ConcurrentHashMap<>();

    public OAuth2ClientCredentialsTokenProvider(Environment environment, ObjectMapper mapper) {
        this.environment = environment;
        this.mapper = mapper;
    }

    public String token(String prefix) {
        CachedToken current = cache.get(prefix);
        if (current != null && current.expiresAt().isAfter(Instant.now().plusSeconds(30))) return current.value();
        synchronized (cache) {
            current = cache.get(prefix);
            if (current != null && current.expiresAt().isAfter(Instant.now().plusSeconds(30))) return current.value();
            String endpoint = required(prefix + ".oauth-token-endpoint");
            String clientId = required(prefix + ".oauth-client-id");
            String clientSecret = required(prefix + ".oauth-client-secret");
            if (!endpoint.startsWith("https://")) throw new IllegalStateException("OAuth token endpoint must use HTTPS");
            String body = "grant_type=client_credentials&client_id=" + enc(clientId)
                    + "&client_secret=" + enc(clientSecret);
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                        .timeout(Duration.ofSeconds(15))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(body)).build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new IllegalStateException("OAuth token endpoint returned HTTP " + response.statusCode());
                }
                var json = mapper.readTree(response.body());
                String token = json.path("access_token").asText();
                long expires = json.path("expires_in").asLong(300);
                if (token.isBlank()) throw new IllegalStateException("OAuth response has no access_token");
                cache.put(prefix, new CachedToken(token, Instant.now().plusSeconds(Math.max(60, expires))));
                return token;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("OAuth token request interrupted", e);
            } catch (Exception e) {
                throw new IllegalStateException("OAuth token request failed", e);
            }
        }
    }

    private String required(String key) {
        String value = environment.getProperty(key);
        if (value == null || value.isBlank()) throw new IllegalStateException("Missing property " + key);
        return value;
    }
    private static String enc(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private record CachedToken(String value, Instant expiresAt) {}
}
