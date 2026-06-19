package com.example.switching.security.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import com.example.switching.AbstractIntegrationTest;
import com.example.switching.security.oauth.service.OAuthTokenService;

@TestPropertySource(properties = {
        "switching.security.oauth.jwt-secret=test-oauth-secret-with-at-least-32-bytes",
        "switching.security.oauth.token-ttl-seconds=3600"
})
class OAuthTokenServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private OAuthTokenService tokenService;

    @Test
    void createTokenReturnsSignedTokenForActiveClient() {
        String token = tokenService.createToken("client-bank-a", Set.of("payments:write"));

        OAuthTokenClaims claims = tokenService.validateToken(token);

        assertThat(claims.clientId()).isEqualTo("client-bank-a");
        assertThat(claims.pspId()).isEqualTo("BANK_A");
        assertThat(claims.scopes()).containsExactly("payments:write");
        assertThat(claims.expiresAt()).isAfter(claims.issuedAt());
    }

    @Test
    void createTokenRejectsScopeOutsideClientGrant() {
        assertThatThrownBy(() -> tokenService.createToken("client-bank-a", Set.of("admin:write")))
                .isInstanceOf(OAuthTokenInvalidException.class)
                .hasMessageContaining("No requested OAuth scopes");
    }

    @Test
    void validateTokenRejectsTamperedToken() {
        String token = tokenService.createToken("client-bank-a", Set.of("payments:read"));
        String tampered = token.substring(0, token.length() - 2) + "xx";

        assertThatThrownBy(() -> tokenService.validateToken(tampered))
                .isInstanceOf(OAuthTokenInvalidException.class)
                .hasMessageContaining("signature");
    }

    @Test
    void revokeTokenRejectsFutureValidation() {
        String token = tokenService.createToken("client-bank-a", Set.of("payments:read"));

        tokenService.revokeToken(token);

        assertThatThrownBy(() -> tokenService.validateToken(token))
                .isInstanceOf(OAuthTokenInvalidException.class)
                .hasMessageContaining("revoked");
    }

    @Test
    void verifyClientSecretChecksStoredSecretHash() {
        assertThat(tokenService.verifyClientSecret("client-bank-a", "secret-bank-a-switching-2026")).isTrue();
        assertThat(tokenService.verifyClientSecret("client-bank-a", "wrong-secret")).isFalse();
    }
}
