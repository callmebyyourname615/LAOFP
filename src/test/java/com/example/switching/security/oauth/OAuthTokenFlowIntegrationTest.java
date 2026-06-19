package com.example.switching.security.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.example.switching.AbstractIntegrationTest;
import com.example.switching.security.oauth.service.OAuthTokenService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * P9 — OAuth 2.0 client_credentials flow.
 *
 * TC-OA-001  Happy path grant → 200, access_token present, token_type=Bearer
 * TC-OA-002  Token reuse — returned token is still valid (no expiry between calls)
 * TC-OA-003  Wrong grant_type → 400
 * TC-OA-004  Wrong client_secret → 401 (LFP-2001)
 * TC-OA-005  Revoke then reject — revoked token rejected as LFP-2001
 *
 * Seeded clients (V20 migration):
 *   client-bank-a / secret-bank-a-switching-2026  scopes: payments:write inquiries:write payments:read
 *   client-bank-b / secret-bank-b-switching-2026  scopes: payments:write inquiries:write payments:read
 */
@TestPropertySource(properties = {
        "switching.security.oauth.jwt-secret=test-oauth-secret-with-at-least-32-bytes",
        "switching.security.oauth.token-ttl-seconds=3600"
})
class OAuthTokenFlowIntegrationTest extends AbstractIntegrationTest {

    private static final String TOKEN_URL  = "/v1/oauth/token";
    private static final String REVOKE_URL = "/v1/oauth/token/revoke";

    private static final String CLIENT_ID_A  = "client-bank-a";
    private static final String SECRET_A     = "secret-bank-a-switching-2026";

    @Autowired private WebApplicationContext webApplicationContext;
    @Autowired private ObjectMapper          objectMapper;
    @Autowired private OAuthTokenService     tokenService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TC-OA-001  Happy path grant
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void grantClientCredentials_returnsSignedBearerToken() throws Exception {
        MvcResult result = mockMvc.perform(post(TOKEN_URL)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type",   "client_credentials")
                        .param("client_id",    CLIENT_ID_A)
                        .param("client_secret", SECRET_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").isNotEmpty())
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.expires_in").value(3600))
                .andExpect(jsonPath("$.scope").isNotEmpty())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        String accessToken = body.path("access_token").asText();

        // token must be a three-part JWT-like structure
        assertThat(accessToken.split("\\.", -1)).hasSize(3);

        // scope must only contain scopes allowed by the client's grant
        String scope = body.path("scope").asText();
        assertThat(scope).contains("payments:write");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TC-OA-002  Token reuse — same token validates again without error
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void issuedToken_isImmediatelyReusable() throws Exception {
        String accessToken = issueToken(CLIENT_ID_A, SECRET_A);

        // validateToken must not throw — token is still within TTL
        OAuthTokenClaims claims = tokenService.validateToken(accessToken);

        assertThat(claims.clientId()).isEqualTo(CLIENT_ID_A);
        assertThat(claims.pspId()).isEqualTo("BANK_A");
        assertThat(claims.scopes()).isNotEmpty();
        assertThat(claims.expiresAt()).isAfter(claims.issuedAt());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TC-OA-003  Wrong grant_type → 400
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void unsupportedGrantType_returns400() throws Exception {
        mockMvc.perform(post(TOKEN_URL)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type",    "password")   // not supported
                        .param("client_id",     CLIENT_ID_A)
                        .param("client_secret", SECRET_A))
                .andExpect(status().isBadRequest());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TC-OA-004  Wrong client_secret → 401 (LFP-2001)
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void wrongClientSecret_returns401WithLfpCode() throws Exception {
        mockMvc.perform(post(TOKEN_URL)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type",    "client_credentials")
                        .param("client_id",     CLIENT_ID_A)
                        .param("client_secret", "totally-wrong-secret"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("LFP-2001"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TC-OA-005  Revoke → revoked token is rejected
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void revokedToken_isRejectedByValidation() throws Exception {
        String accessToken = issueToken(CLIENT_ID_A, SECRET_A);

        // token is valid before revocation
        assertThat(tokenService.validateToken(accessToken)).isNotNull();

        // revoke it
        mockMvc.perform(post(REVOKE_URL)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("token", accessToken))
                .andExpect(status().isOk());

        // token must now be rejected
        org.junit.jupiter.api.Assertions.assertThrows(
                OAuthTokenInvalidException.class,
                () -> tokenService.validateToken(accessToken),
                "Revoked token must throw OAuthTokenInvalidException");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private String issueToken(String clientId, String secret) throws Exception {
        MvcResult result = mockMvc.perform(post(TOKEN_URL)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type",    "client_credentials")
                        .param("client_id",     clientId)
                        .param("client_secret", secret))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("access_token").asText();
    }
}
