package com.example.switching.security.oauth;

import java.time.Instant;
import java.util.Set;

public record OAuthTokenClaims(
        String clientId,
        String pspId,
        Set<String> scopes,
        Instant issuedAt,
        Instant expiresAt) {
}
