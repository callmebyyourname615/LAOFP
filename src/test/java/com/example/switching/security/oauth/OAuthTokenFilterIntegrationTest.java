package com.example.switching.security.oauth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.example.switching.AbstractIntegrationTest;
import com.example.switching.security.oauth.service.OAuthTokenService;

/**
 * P9 — OAuthTokenFilter integration tests.
 *
 * Verifies that the Bearer-token filter correctly gates bank-facing endpoints
 * when {@code switching.security.oauth.enabled=true}.
 *
 * TC-OF-001  Valid Bearer token on BANK endpoint → 200
 * TC-OF-002  Tampered / invalid Bearer token     → 401 LFP-2001
 * TC-OF-003  Revoked Bearer token                → 401 LFP-2001
 * TC-OF-004  No auth header at all               → 401 (neither filter sets context)
 *
 * The X-API-Key path is tested separately in SecurityAuthorizationIntegrationTest;
 * this class focuses exclusively on the OAuth Bearer path.
 */
@TestPropertySource(properties = {
        "switching.security.api-key.enabled=true",
        "switching.security.oauth.enabled=true",
        "switching.security.oauth.jwt-secret=test-oauth-secret-with-at-least-32-bytes",
        "switching.security.oauth.token-ttl-seconds=3600"
})
class OAuthTokenFilterIntegrationTest extends AbstractIntegrationTest {

    /** A BANK-role endpoint that returns 200 + JSON list when auth passes. */
    private static final String BANK_ENDPOINT = "/api/transfers";

    @Autowired private WebApplicationContext  webApplicationContext;
    @Autowired private FilterChainProxy       springSecurityFilterChain;
    @Autowired private OAuthTokenService      tokenService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .addFilters(springSecurityFilterChain)
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TC-OF-001  Valid Bearer token → endpoint returns 200
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void validBearerToken_allowsAccessToBankEndpoint() throws Exception {
        String token = tokenService.createToken("client-bank-a", Set.of("payments:read"));

        mockMvc.perform(get(BANK_ENDPOINT)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TC-OF-002  Tampered token → 401 with LFP-2001
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void tamperedBearerToken_returns401WithLfpCode() throws Exception {
        String token  = tokenService.createToken("client-bank-a", Set.of("payments:read"));
        String tampered = token.substring(0, token.length() - 4) + "XXXX";

        mockMvc.perform(get(BANK_ENDPOINT)
                        .header("Authorization", "Bearer " + tampered))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("LFP-2001"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TC-OF-003  Revoked token → 401 with LFP-2001
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void revokedBearerToken_returns401WithLfpCode() throws Exception {
        String token = tokenService.createToken("client-bank-a", Set.of("payments:read"));
        tokenService.revokeToken(token);

        mockMvc.perform(get(BANK_ENDPOINT)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("LFP-2001"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TC-OF-004  No auth header → 401 (chain falls through, no context set)
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void noAuthHeader_returns401() throws Exception {
        mockMvc.perform(get(BANK_ENDPOINT))
                .andExpect(status().isUnauthorized());
    }
}
