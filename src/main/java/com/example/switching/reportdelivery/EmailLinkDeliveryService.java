package com.example.switching.reportdelivery;

import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import com.example.switching.notifications.NotificationDeliveryControlService;

@Component
public class EmailLinkDeliveryService implements ReportDeliveryChannel {

    private static final long LINK_TTL_SECONDS = 86_400L;

    private final NotificationDeliveryControlService notifications;
    private final Environment environment;

    public EmailLinkDeliveryService(
            NotificationDeliveryControlService notifications,
            Environment environment) {
        this.notifications = notifications;
        this.environment = environment;
    }

    @Override
    public DeliveryChannel channel() {
        return DeliveryChannel.EMAIL_LINK;
    }

    @Override
    public DeliveryResult deliver(
            ReportArtifactService.StoredArtifact artifact,
            Map<String, Object> destination) {
        String recipient = required(destination, "recipientReference");
        String baseUrl = validateBaseUrl(environment.getProperty(
                "switching.phase-ii.report-delivery.download-base-url"));
        String secret = environment.getProperty(
                "switching.phase-ii.report-delivery.link-signing-secret");
        if (secret == null
                || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("Report link signing configuration is invalid");
        }
        long expires = Instant.now().plusSeconds(LINK_TTL_SECONDS).getEpochSecond();
        String data = artifact.id() + "|" + expires;
        String token = hmac(data, secret);
        String url = baseUrl
                + "/v1/reports/download/" + artifact.id()
                + "?expires=" + expires
                + "&token=" + token;
        notifications.queue(
                "REPORT-" + artifact.id(),
                "REPORT_READY",
                "EMAIL",
                "en",
                recipient,
                Map.of(
                        "downloadUrl", url,
                        "fileName", artifact.fileName(),
                        "expiresAt", Instant.ofEpochSecond(expires).toString()));
        return new DeliveryResult("email-link:" + artifact.id());
    }

    private static String validateBaseUrl(String raw) {
        try {
            URI uri = URI.create(raw == null ? "" : raw.trim());
            String host = uri.getHost();
            if (host == null
                    || uri.getUserInfo() != null
                    || uri.getQuery() != null
                    || uri.getFragment() != null
                    || !("https".equalsIgnoreCase(uri.getScheme())
                            || ("http".equalsIgnoreCase(uri.getScheme())
                                    && isLoopback(host)))) {
                throw new IllegalArgumentException(
                        "Report download base URL must use HTTPS; HTTP is allowed only for loopback tests");
            }
            String value = uri.toString();
            return value.endsWith("/")
                    ? value.substring(0, value.length() - 1)
                    : value;
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Invalid report download base URL", exception);
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

    private static String required(Map<String, Object> destination, String key) {
        Object value = destination.get(key);
        if (value == null
                || String.valueOf(value).isBlank()
                || String.valueOf(value).length() > 256) {
            throw new IllegalArgumentException("Missing or invalid email " + key);
        }
        return String.valueOf(value).trim();
    }

    public static String hmac(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"));
            return HexFormat.of().formatHex(
                    mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
