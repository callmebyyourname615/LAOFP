package com.example.switching.crossborder.adapter;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.core.env.Environment;

import com.example.switching.crossborder.dto.RailInstruction;
import com.example.switching.crossborder.dto.RailInstructionEvent;
import com.example.switching.crossborder.dto.RailTransactionRef;
import com.example.switching.crossborder.service.OAuth2ClientCredentialsTokenProvider;
import com.example.switching.crossborder.service.RailHttpTransport;
import com.example.switching.crossborder.service.RailMessageJournalService;
import com.example.switching.crossborder.service.RailSignatureVerifier;
import com.fasterxml.jackson.databind.ObjectMapper;

public abstract class AbstractJsonRailAdapter implements CrossBorderRailAdapter {

    protected final Environment environment;
    protected final RailMessageJournalService journal;
    protected final RailHttpTransport transport;
    protected final RailSignatureVerifier signatures;
    protected final ObjectMapper mapper;
    protected final OAuth2ClientCredentialsTokenProvider oauth;

    protected AbstractJsonRailAdapter(
            Environment environment,
            RailMessageJournalService journal,
            RailHttpTransport transport,
            RailSignatureVerifier signatures,
            ObjectMapper mapper,
            OAuth2ClientCredentialsTokenProvider oauth) {
        this.environment = environment;
        this.journal = journal;
        this.transport = transport;
        this.signatures = signatures;
        this.mapper = mapper;
        this.oauth = oauth;
    }

    protected abstract String prefix();

    @Override
    public RailTransactionRef submit(RailInstruction instruction) {
        assertMtls();
        String externalReference = blank(instruction.externalRef())
                ? deterministicExternalReference(instruction.internalRef())
                : instruction.externalRef();
        UUID messageId = journal.recordOutbound(
                rail(),
                externalReference,
                instruction.internalRef(),
                "PAYMENT",
                instruction);
        try {
            String body = mapper.writeValueAsString(instruction);
            RailHttpTransport.Response response = transport.post(
                    required("endpoint"),
                    body,
                    headers(body),
                    Duration.ofSeconds(environment.getProperty(
                            prefix() + ".timeout-seconds",
                            Long.class,
                            30L)));
            String partnerReference = blank(response.externalReference())
                    ? externalReference
                    : response.externalReference();
            journal.complete(
                    messageId,
                    Map.of(
                            "statusCode", response.statusCode(),
                            "body", response.body()),
                    "ACKNOWLEDGED",
                    "HTTP_" + response.statusCode());
            return new RailTransactionRef(
                    rail(),
                    partnerReference,
                    "ACKNOWLEDGED",
                    "HTTP_" + response.statusCode());
        } catch (RuntimeException exception) {
            journal.fail(messageId, exception.getClass().getSimpleName());
            throw exception;
        } catch (Exception exception) {
            journal.fail(messageId, exception.getClass().getSimpleName());
            throw new IllegalStateException("Unable to submit rail instruction", exception);
        }
    }

    @Override
    public RailInstructionEvent acceptInbound(
            String externalReference,
            String messageType,
            String payload,
            String signature) {
        if (blank(externalReference) || blank(messageType) || blank(payload)) {
            throw new IllegalArgumentException("Inbound rail message is incomplete");
        }
        signatures.verifyHmacSha256(payload, signature, required("hmac-secret"));
        String internalReference = extractInternalRef(payload);
        UUID id = journal.recordInbound(
                rail(),
                externalReference,
                internalReference,
                messageType,
                mapperValue(payload));
        journal.complete(
                id,
                Map.of("accepted", true),
                "VALIDATED",
                "SIGNATURE_OK");
        return new RailInstructionEvent(
                rail(),
                externalReference,
                internalReference,
                messageType,
                "VALIDATED",
                Instant.now());
    }

    protected Map<String, String> headers(String body) {
        Map<String, String> headers = new LinkedHashMap<>();
        String partnerId = environment.getProperty(prefix() + ".partner-id");
        if (!blank(partnerId)) {
            headers.put("X-Partner-Id", partnerId);
        }
        String token = environment.getProperty(prefix() + ".oauth-token");
        if (blank(token)
                && !blank(environment.getProperty(prefix() + ".oauth-token-endpoint"))) {
            token = oauth.token(prefix());
        }
        if (!blank(token)) {
            headers.put("Authorization", "Bearer " + token);
        }
        String secret = environment.getProperty(prefix() + ".hmac-secret");
        if (!blank(secret)) {
            headers.put("X-Signature", hmac(body, secret));
        }
        return Map.copyOf(headers);
    }

    protected String extractInternalRef(String payload) {
        try {
            String value = mapper.readTree(payload).path("internalRef").asText("");
            if (blank(value)) {
                throw new IllegalArgumentException("Inbound message has no internalRef");
            }
            return value;
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid inbound JSON", exception);
        }
    }

    protected String required(String key) {
        String value = environment.getProperty(prefix() + "." + key);
        if (blank(value)) {
            throw new IllegalStateException(
                    "Missing rail property " + prefix() + "." + key);
        }
        return value;
    }

    protected static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private void assertMtls() {
        if (environment.getProperty(prefix() + ".mtls-required", Boolean.class, true)) {
            String keyStore = System.getProperty("javax.net.ssl.keyStore");
            String trustStore = System.getProperty("javax.net.ssl.trustStore");
            if (blank(keyStore) || blank(trustStore)) {
                throw new IllegalStateException(
                        "mTLS requires JVM keyStore and trustStore");
            }
        }
    }

    private String deterministicExternalReference(String internalReference) {
        if (blank(internalReference)) {
            throw new IllegalArgumentException("Rail internal reference is required");
        }
        return rail() + "-" + sha256(rail() + "|" + internalReference).substring(0, 32);
    }

    private Object mapperValue(String payload) {
        try {
            return mapper.readTree(payload);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid inbound JSON", exception);
        }
    }

    private static String hmac(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"));
            return HexFormat.of().formatHex(
                    mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
