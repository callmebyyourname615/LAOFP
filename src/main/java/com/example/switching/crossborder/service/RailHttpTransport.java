package com.example.switching.crossborder.service;

import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

@Component
public class RailHttpTransport {

    private static final int MAX_REQUEST_BYTES = 2 * 1024 * 1024;
    private static final int MAX_RESPONSE_BYTES = 4 * 1024 * 1024;
    private static final Set<String> FORBIDDEN_HEADERS =
            Set.of("host", "content-length", "transfer-encoding", "connection");

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    public Response post(
            String endpoint,
            String body,
            Map<String, String> headers,
            Duration timeout) {
        URI uri = validateEndpoint(endpoint);
        validateBody(body);
        Duration boundedTimeout = validateTimeout(timeout);
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                    .timeout(boundedTimeout)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            body,
                            StandardCharsets.UTF_8));
            if (headers != null) {
                headers.forEach((name, value) -> addHeader(builder, name, value));
            }
            HttpResponse<String> response = client.send(
                    builder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            String responseBody = response.body() == null ? "" : response.body();
            if (responseBody.getBytes(StandardCharsets.UTF_8).length > MAX_RESPONSE_BYTES) {
                throw new IllegalStateException("Rail response exceeds the configured size limit");
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RailHttpException(response.statusCode());
            }
            return new Response(
                    response.statusCode(),
                    responseBody,
                    response.headers().firstValue("X-External-Reference").orElse(null));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Rail call interrupted", exception);
        } catch (RailHttpException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Rail call failed", exception);
        }
    }

    private static URI validateEndpoint(String endpoint) {
        try {
            URI uri = URI.create(endpoint == null ? "" : endpoint.trim());
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (host == null
                    || uri.getUserInfo() != null
                    || uri.getFragment() != null
                    || !("https".equalsIgnoreCase(scheme)
                            || ("http".equalsIgnoreCase(scheme) && isLoopback(host)))) {
                throw new IllegalArgumentException(
                        "Rail endpoint must be HTTPS; HTTP is allowed only for loopback tests");
            }
            return uri;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid cross-border rail endpoint", exception);
        }
    }

    private static boolean isLoopback(String host) {
        try {
            return "localhost".equalsIgnoreCase(host)
                    || InetAddress.getByName(host).isLoopbackAddress();
        } catch (Exception exception) {
            return false;
        }
    }

    private static void validateBody(String body) {
        if (body == null
                || body.isBlank()
                || body.getBytes(StandardCharsets.UTF_8).length > MAX_REQUEST_BYTES) {
            throw new IllegalArgumentException("Rail request body is empty or too large");
        }
    }

    private static Duration validateTimeout(Duration timeout) {
        if (timeout == null
                || timeout.compareTo(Duration.ofSeconds(1)) < 0
                || timeout.compareTo(Duration.ofMinutes(2)) > 0) {
            throw new IllegalArgumentException("Rail timeout must be between 1 and 120 seconds");
        }
        return timeout;
    }

    private static void addHeader(
            HttpRequest.Builder builder,
            String name,
            String value) {
        String normalized = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()
                || FORBIDDEN_HEADERS.contains(normalized)
                || value == null
                || value.isBlank()
                || value.indexOf('\r') >= 0
                || value.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("Invalid rail HTTP header");
        }
        builder.header(name, value);
    }

    public record Response(
            int statusCode,
            String body,
            String externalReference) {}

    public static final class RailHttpException extends IllegalStateException {
        private final int statusCode;

        RailHttpException(int statusCode) {
            super("Rail returned HTTP " + statusCode);
            this.statusCode = statusCode;
        }

        public int statusCode() {
            return statusCode;
        }
    }
}
