package com.example.switching.aml.sanctions.provider;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.example.switching.aml.config.AmlProperties;

/** HTTPS-only downloader with bounded payloads and exponential retry. */
@Component
public class SanctionsHttpClient {

    private final AmlProperties properties;
    private final HttpClient client;

    public SanctionsHttpClient(AmlProperties properties) {
        this.properties = properties;
        this.client = HttpClient.newBuilder()
                .connectTimeout(properties.getSanctions().getConnectTimeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public byte[] get(String url, Map<String, String> headers, boolean allowInsecureHttp) {
        URI uri = parseAndValidateUri(url, allowInsecureHttp);
        int attempts = Math.max(1, properties.getSanctions().getRetryAttempts());
        Duration initialBackoff = properties.getSanctions().getRetryInitialBackoff();
        Throwable lastError = null;

        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(uri)
                        .GET()
                        .timeout(properties.getSanctions().getReadTimeout())
                        .header("Accept", "application/xml, application/json;q=0.9, */*;q=0.1")
                        .header("User-Agent", "switching-sanctions-sync/1.0");
                headers.forEach((name, value) -> {
                    if (name != null && !name.isBlank() && value != null && !value.isBlank()) {
                        requestBuilder.header(name, value);
                    }
                });

                HttpResponse<InputStream> response = client.send(
                        requestBuilder.build(), HttpResponse.BodyHandlers.ofInputStream());
                try (InputStream responseBody = response.body()) {
                    validateRedirectTarget(response.uri(), allowInsecureHttp);
                    validateDeclaredPayloadSize(response);

                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        return readBounded(responseBody);
                    }
                    if (response.statusCode() != 429 && response.statusCode() < 500) {
                        throw new SanctionsProviderException(
                                "Sanctions endpoint returned non-retryable HTTP " + response.statusCode());
                    }
                    lastError = new IOException("Retryable HTTP " + response.statusCode());
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new SanctionsProviderException("Sanctions download interrupted", interrupted);
            } catch (IOException | RuntimeException error) {
                if (error instanceof SanctionsProviderException spe) {
                    throw spe;
                }
                lastError = error;
            }

            if (attempt < attempts) {
                sleepBackoff(initialBackoff, attempt);
            }
        }
        throw new SanctionsProviderException(
                "Sanctions download failed after " + attempts + " attempt(s)", lastError);
    }

    private URI parseAndValidateUri(String value, boolean allowInsecureHttp) {
        if (value == null || value.isBlank()) {
            throw new SanctionsProviderException("Sanctions provider URL is blank");
        }
        URI uri;
        try {
            uri = URI.create(value.trim());
        } catch (IllegalArgumentException invalid) {
            throw new SanctionsProviderException("Invalid sanctions provider URL", invalid);
        }
        validateRedirectTarget(uri, allowInsecureHttp);
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new SanctionsProviderException("Sanctions provider URL must include a host");
        }
        return uri;
    }

    private void validateRedirectTarget(URI uri, boolean allowInsecureHttp) {
        String scheme = uri.getScheme();
        if (!"https".equalsIgnoreCase(scheme)
                && !(allowInsecureHttp && "http".equalsIgnoreCase(scheme))) {
            throw new SanctionsProviderException("Sanctions provider must use HTTPS");
        }
    }

    private void validateDeclaredPayloadSize(HttpResponse<?> response) {
        long limit = properties.getSanctions().getMaxPayloadBytes();
        long declared = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
        if (declared > limit) {
            throw new SanctionsProviderException(
                    "Sanctions payload exceeds configured limit of " + limit + " bytes");
        }
    }

    private byte[] readBounded(InputStream body) throws IOException {
        long configuredLimit = properties.getSanctions().getMaxPayloadBytes();
        if (configuredLimit < 1 || configuredLimit >= Integer.MAX_VALUE) {
            throw new SanctionsProviderException(
                    "Sanctions max-payload-bytes must be between 1 and " + (Integer.MAX_VALUE - 1));
        }
        int limit = Math.toIntExact(configuredLimit);
        byte[] payload = body.readNBytes(limit + 1);
        if (payload.length > limit) {
            throw new SanctionsProviderException(
                    "Sanctions payload exceeds configured limit of " + configuredLimit + " bytes");
        }
        return payload;
    }

    private void sleepBackoff(Duration initial, int completedAttempt) {
        long multiplier = 1L << Math.min(completedAttempt - 1, 10);
        long millis = Math.min(initial.toMillis() * multiplier, Duration.ofSeconds(30).toMillis());
        try {
            Thread.sleep(Math.max(1L, millis));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new SanctionsProviderException("Sanctions retry interrupted", interrupted);
        }
    }
}
