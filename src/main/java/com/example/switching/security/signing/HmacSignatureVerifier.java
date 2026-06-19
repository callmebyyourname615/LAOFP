package com.example.switching.security.signing;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class HmacSignatureVerifier {

    private static final String HMAC_SHA256 = "HmacSHA256";

    private final Clock clock;
    private final long timestampToleranceSeconds;

    @org.springframework.beans.factory.annotation.Autowired
    public HmacSignatureVerifier(
            @Value("${switching.security.signing.timestamp-tolerance-seconds:30}") long timestampToleranceSeconds) {
        this(Clock.systemUTC(), timestampToleranceSeconds);
    }

    HmacSignatureVerifier(Clock clock, long timestampToleranceSeconds) {
        this.clock = clock;
        this.timestampToleranceSeconds = timestampToleranceSeconds;
    }

    public void verify(
            String method,
            String requestUri,
            String queryString,
            String body,
            String xRequestSignature,
            String xTimestamp,
            String clientSecret) {
        if (isBlank(xRequestSignature) || isBlank(xTimestamp) || isBlank(clientSecret)) {
            throw new SignatureVerificationException("Missing request signature headers");
        }

        Instant requestTime = parseTimestamp(xTimestamp.trim());
        long skew = Math.abs(Instant.now(clock).getEpochSecond() - requestTime.getEpochSecond());
        if (skew > timestampToleranceSeconds) {
            throw new SignatureVerificationException("Request signature timestamp is outside tolerance");
        }

        String canonical = canonicalString(method, requestUri, queryString, xTimestamp.trim(), body);
        String expected = signHex(canonical, clientSecret.trim());
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                xRequestSignature.trim().toLowerCase().getBytes(StandardCharsets.UTF_8))) {
            throw new SignatureVerificationException("Invalid request signature");
        }
    }

    public static String signHex(String canonicalString, String clientSecret) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(clientSecret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            return HexFormat.of().formatHex(mac.doFinal(canonicalString.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to compute request signature", ex);
        }
    }

    public static String canonicalString(
            String method,
            String requestUri,
            String queryString,
            String timestamp,
            String body) {
        return method.toUpperCase()
                + "\n" + requestUri
                + "\n" + (queryString == null ? "" : queryString)
                + "\n" + timestamp
                + "\n" + (body == null ? "" : body);
    }

    private Instant parseTimestamp(String timestamp) {
        try {
            return Instant.ofEpochSecond(Long.parseLong(timestamp));
        } catch (NumberFormatException ignored) {
            try {
                return Instant.parse(timestamp);
            } catch (DateTimeParseException ex) {
                throw new SignatureVerificationException("Invalid request signature timestamp");
            }
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
