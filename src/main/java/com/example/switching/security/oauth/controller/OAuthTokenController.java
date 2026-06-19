package com.example.switching.security.oauth.controller;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.switching.security.oauth.OAuthTokenClaims;
import com.example.switching.security.oauth.OAuthTokenInvalidException;
import com.example.switching.security.oauth.dto.OAuthTokenResponse;
import com.example.switching.security.oauth.service.OAuthTokenService;

/**
 * OAuth 2.0 token endpoint — RFC 6749 client_credentials grant.
 *
 * <p>All paths under {@code /v1/oauth/**} are permit-all in SecurityConfig so
 * that PSPs can obtain a token without needing a pre-existing credential in the
 * request header.  Authentication is performed here by verifying
 * {@code client_id} + {@code client_secret} against the {@code oauth_clients}
 * table.
 *
 * <h3>Endpoints</h3>
 * <ul>
 *   <li>{@code POST /v1/oauth/token} — issue a signed Bearer token</li>
 *   <li>{@code POST /v1/oauth/token/revoke} — revoke an active token (RFC 7009)</li>
 * </ul>
 */
@RestController
@RequestMapping("${switching.api.v1-prefix}/oauth")
public class OAuthTokenController {

    private static final String GRANT_CLIENT_CREDENTIALS = "client_credentials";

    private final OAuthTokenService tokenService;
    private final long tokenTtlSeconds;

    public OAuthTokenController(
            OAuthTokenService tokenService,
            @Value("${switching.security.oauth.token-ttl-seconds:3600}") long tokenTtlSeconds) {
        this.tokenService = tokenService;
        this.tokenTtlSeconds = tokenTtlSeconds;
    }

    /**
     * RFC 6749 §4.4 — client_credentials grant.
     *
     * <p>Request (application/x-www-form-urlencoded):
     * <pre>
     * grant_type=client_credentials
     * &amp;client_id=client-bank-a
     * &amp;client_secret=secret-bank-a-switching-2026
     * &amp;scope=payments:write payments:read        (optional)
     * </pre>
     *
     * <p>Response:
     * <pre>
     * {"access_token":"eyJ...","token_type":"Bearer","expires_in":3600,"scope":"payments:write"}
     * </pre>
     */
    @PostMapping(
            value = "/token",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<OAuthTokenResponse> token(
            @RequestParam("grant_type")   String grantType,
            @RequestParam("client_id")    String clientId,
            @RequestParam("client_secret") String clientSecret,
            @RequestParam(value = "scope", required = false) String scope) {

        if (!GRANT_CLIENT_CREDENTIALS.equals(grantType)) {
            throw new IllegalArgumentException(
                    "unsupported_grant_type: only client_credentials is supported");
        }

        if (!StringUtils.hasText(clientId)) {
            throw new OAuthTokenInvalidException("client_id is required");
        }
        if (!StringUtils.hasText(clientSecret)) {
            throw new OAuthTokenInvalidException("client_secret is required");
        }

        if (!tokenService.verifyClientSecret(clientId, clientSecret)) {
            throw new OAuthTokenInvalidException("Invalid client credentials");
        }

        Set<String> requestedScopes = parseScope(scope);
        String accessToken = tokenService.createToken(clientId, requestedScopes);

        // Read the effective scopes that were embedded in the token.
        // We call validateToken here only to read the claims — at this point the
        // token is brand new and guaranteed valid, so no exception can be thrown.
        OAuthTokenClaims claims = tokenService.validateToken(accessToken);
        String effectiveScope = String.join(" ", claims.scopes());

        return ResponseEntity.ok(
                OAuthTokenResponse.bearer(accessToken, tokenTtlSeconds, effectiveScope));
    }

    /**
     * RFC 7009 §2 — token revocation.
     *
     * <p>Request (application/x-www-form-urlencoded):
     * <pre>token=eyJ...</pre>
     *
     * <p>Response: 200 OK with empty body (revocation is idempotent per spec).
     */
    @PostMapping(
            value = "/token/revoke",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Void> revoke(
            @RequestParam("token") String token) {

        if (!StringUtils.hasText(token)) {
            throw new OAuthTokenInvalidException("token is required");
        }

        tokenService.revokeToken(token);
        return ResponseEntity.ok().build();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Set<String> parseScope(String scope) {
        if (!StringUtils.hasText(scope)) {
            return Set.of();
        }
        return Arrays.stream(scope.trim().split("\\s+"))
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
