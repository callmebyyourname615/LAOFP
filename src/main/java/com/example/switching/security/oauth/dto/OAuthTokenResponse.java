package com.example.switching.security.oauth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * RFC 6749 §5.1 — successful token response for the client_credentials grant.
 *
 * <pre>
 * {
 *   "access_token":  "eyJ...",
 *   "token_type":    "Bearer",
 *   "expires_in":    3600,
 *   "scope":         "payments:write payments:read"
 * }
 * </pre>
 */
public record OAuthTokenResponse(

        @JsonProperty("access_token")
        String accessToken,

        @JsonProperty("token_type")
        String tokenType,

        @JsonProperty("expires_in")
        long expiresIn,

        String scope
) {
    public static OAuthTokenResponse bearer(String accessToken, long expiresIn, String scope) {
        return new OAuthTokenResponse(accessToken, "Bearer", expiresIn, scope);
    }
}
