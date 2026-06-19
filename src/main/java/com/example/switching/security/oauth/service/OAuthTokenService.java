package com.example.switching.security.oauth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.example.switching.participant.exception.ParticipantSuspendedException;
import com.example.switching.security.oauth.OAuthTokenClaims;
import com.example.switching.security.oauth.OAuthTokenInvalidException;
import com.example.switching.security.oauth.entity.OAuthClientEntity;
import com.example.switching.security.oauth.enums.OAuthClientStatus;
import com.example.switching.security.oauth.repository.OAuthClientRepository;
import com.example.switching.security.util.ApiKeyHashUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class OAuthTokenService {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final Base64.Encoder B64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64_URL_DECODER = Base64.getUrlDecoder();

    private final OAuthClientRepository clientRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final String jwtSecret;
    private final long tokenTtlSeconds;

    /** Signatures of individually-revoked tokens (RFC 7009). */
    private final Set<String> revokedTokenSignatures = ConcurrentHashMap.newKeySet();

    /**
     * Per-client rotation epoch (seconds).
     * Any token with {@code iat <= rotationEpoch} for that client is invalidated.
     * Set by {@link #markClientRotated(String, long)}.
     */
    private final Map<String, Long> clientRotationEpochs = new ConcurrentHashMap<>();

    @Autowired
    public OAuthTokenService(
            OAuthClientRepository clientRepository,
            ObjectMapper objectMapper,
            @Value("${switching.security.oauth.jwt-secret:dev-test-oauth-secret-change-me-32-bytes}") String jwtSecret,
            @Value("${switching.security.oauth.token-ttl-seconds:3600}") long tokenTtlSeconds) {
        this(clientRepository, objectMapper, Clock.systemUTC(), jwtSecret, tokenTtlSeconds);
    }

    OAuthTokenService(
            OAuthClientRepository clientRepository,
            ObjectMapper objectMapper,
            Clock clock,
            String jwtSecret,
            long tokenTtlSeconds) {
        this.clientRepository = clientRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.jwtSecret = jwtSecret;
        this.tokenTtlSeconds = tokenTtlSeconds;
    }

    public String createToken(String clientId, Set<String> requestedScopes) {
        OAuthClientEntity client = activeClient(clientId);
        Set<String> allowedScopes = parseScopes(client.getScopes());
        Set<String> scopes = requestedScopes == null || requestedScopes.isEmpty()
                ? allowedScopes
                : requestedScopes.stream().filter(allowedScopes::contains).collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        if (scopes.isEmpty()) {
            throw new OAuthTokenInvalidException("No requested OAuth scopes are allowed for client");
        }

        Instant now = Instant.now(clock);
        Instant expiresAt = now.plusSeconds(tokenTtlSeconds);
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", "HS256");
        header.put("typ", "JWT");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("jti", UUID.randomUUID().toString()); // unique token ID — prevents same-second collision
        payload.put("sub", client.getClientId());
        payload.put("psp", client.getPspId());
        payload.put("scope", String.join(" ", scopes));
        payload.put("iat", now.getEpochSecond());
        payload.put("exp", expiresAt.getEpochSecond());

        String signingInput = base64Json(header) + "." + base64Json(payload);
        return signingInput + "." + sign(signingInput);
    }

    public OAuthTokenClaims validateToken(String bearerToken) {
        String token = normalizeBearerToken(bearerToken);
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3) {
            throw new OAuthTokenInvalidException("Invalid OAuth token format");
        }
        if (revokedTokenSignatures.contains(parts[2])) {
            throw new OAuthTokenInvalidException("OAuth token has been revoked");
        }

        String signingInput = parts[0] + "." + parts[1];
        String expectedSignature = sign(signingInput);
        if (!MessageDigest.isEqual(
                expectedSignature.getBytes(StandardCharsets.UTF_8),
                parts[2].getBytes(StandardCharsets.UTF_8))) {
            throw new OAuthTokenInvalidException("Invalid OAuth token signature");
        }

        Map<String, Object> payload = decodePayload(parts[1]);
        String clientId = stringClaim(payload, "sub");
        long iat = longClaim(payload, "iat");

        // Check credential-rotation invalidation: any token issued at or before the
        // rotation epoch for this client is no longer valid.
        Long rotationEpoch = clientRotationEpochs.get(clientId);
        if (rotationEpoch != null && iat <= rotationEpoch) {
            throw new OAuthTokenInvalidException("Token invalidated by credential rotation");
        }

        OAuthClientEntity client = activeClient(clientId);  // also checks SUSPENDED → 403
        long exp = longClaim(payload, "exp");
        if (Instant.now(clock).getEpochSecond() >= exp) {
            throw new OAuthTokenInvalidException("OAuth token has expired");
        }

        return new OAuthTokenClaims(
                clientId,
                client.getPspId(),
                parseScopes(stringClaim(payload, "scope")),
                Instant.ofEpochSecond(iat),
                Instant.ofEpochSecond(exp));
    }

    public void revokeToken(String token) {
        String normalized = normalizeBearerToken(token);
        String[] parts = normalized.split("\\.", -1);
        if (parts.length != 3) {
            throw new OAuthTokenInvalidException("Invalid OAuth token format");
        }
        revokedTokenSignatures.add(parts[2]);
    }

    /**
     * Marks all tokens issued for {@code clientId} at or before
     * {@code rotationEpochSeconds} as invalid.
     *
     * <p>Called by {@code ParticipantCredentialService.rotateCredentials()} after
     * updating the client secret hash so that any Bearer tokens the PSP issued
     * before the rotation are immediately rejected.
     */
    public void markClientRotated(String clientId, long rotationEpochSeconds) {
        clientRotationEpochs.put(clientId, rotationEpochSeconds);
    }

    public boolean verifyClientSecret(String clientId, String clientSecret) {
        OAuthClientEntity client = activeClient(clientId);
        return MessageDigest.isEqual(
                client.getClientSecretHash().getBytes(StandardCharsets.UTF_8),
                ApiKeyHashUtil.hash(clientSecret).getBytes(StandardCharsets.UTF_8));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private OAuthClientEntity activeClient(String clientId) {
        if (!StringUtils.hasText(clientId)) {
            throw new OAuthTokenInvalidException("OAuth client_id is required");
        }
        OAuthClientEntity client = clientRepository.findById(clientId.trim())
                .orElseThrow(() -> new OAuthTokenInvalidException("OAuth client not found"));

        // Differentiate SUSPENDED (403 LFP-2004) from REVOKED (401 LFP-2001).
        if (client.getStatus() == OAuthClientStatus.SUSPENDED) {
            throw new ParticipantSuspendedException(client.getPspId());
        }
        if (client.getStatus() != OAuthClientStatus.ACTIVE) {
            throw new OAuthTokenInvalidException("OAuth client is not active");
        }
        LocalDateTime expiresAt = client.getExpiresAt();
        if (expiresAt != null && LocalDateTime.now(clock).isAfter(expiresAt)) {
            throw new OAuthTokenInvalidException("OAuth client has expired");
        }
        return client;
    }

    private String normalizeBearerToken(String bearerToken) {
        if (!StringUtils.hasText(bearerToken)) {
            throw new OAuthTokenInvalidException("OAuth bearer token is required");
        }
        String trimmed = bearerToken.trim();
        if (trimmed.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return trimmed.substring(7).trim();
        }
        return trimmed;
    }

    private Set<String> parseScopes(String scopes) {
        if (!StringUtils.hasText(scopes)) {
            return Set.of();
        }
        return Stream.of(scopes.trim().split("\\s+"))
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    private String base64Json(Map<String, Object> value) {
        try {
            return B64_URL_ENCODER.encodeToString(objectMapper.writeValueAsBytes(value));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to encode OAuth token", ex);
        }
    }

    private Map<String, Object> decodePayload(String payloadPart) {
        try {
            byte[] json = B64_URL_DECODER.decode(payloadPart);
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception ex) {
            throw new OAuthTokenInvalidException("Invalid OAuth token payload");
        }
    }

    private String stringClaim(Map<String, Object> payload, String claim) {
        Object value = payload.get(claim);
        if (value == null || !StringUtils.hasText(String.valueOf(value))) {
            throw new OAuthTokenInvalidException("Missing OAuth token claim: " + claim);
        }
        return String.valueOf(value);
    }

    private long longClaim(Map<String, Object> payload, String claim) {
        Object value = payload.get(claim);
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception ex) {
            throw new OAuthTokenInvalidException("Invalid OAuth token claim: " + claim);
        }
    }

    private String sign(String signingInput) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            return B64_URL_ENCODER.encodeToString(mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to sign OAuth token", ex);
        }
    }
}
