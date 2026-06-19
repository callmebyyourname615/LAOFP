package com.example.switching.security;

import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.example.switching.AbstractIntegrationTest;
import com.example.switching.security.signing.HmacSignatureVerifier;

@TestPropertySource(properties = {
        "switching.security.api-key.enabled=true",
        "switching.security.signing.enabled=true",
        "switching.security.signing.timestamp-tolerance-seconds=30"
})
class RequestSignatureIntegrationTest extends AbstractIntegrationTest {

    private static final String BANK_KEY = "sk-bank-a-switching-2026";

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private FilterChainProxy springSecurityFilterChain;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .addFilters(springSecurityFilterChain)
                .build();
    }

    @Test
    void missingSignatureHeaderReturns401() throws Exception {
        mockMvc.perform(post("/api/inquiries")
                        .header("X-API-Key", BANK_KEY)
                        .header("X-Timestamp", String.valueOf(Instant.now().getEpochSecond()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(inquiryBody("SIG-MISSING")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode", equalTo("LFP-2003")));
    }

    @Test
    void staleTimestampReturns401() throws Exception {
        String body = inquiryBody("SIG-STALE");
        String timestamp = String.valueOf(Instant.now().minusSeconds(60).getEpochSecond());

        mockMvc.perform(post("/api/inquiries")
                        .header("X-API-Key", BANK_KEY)
                        .header("X-Timestamp", timestamp)
                        .header("X-Request-Signature", signature("POST", "/api/inquiries", null, timestamp, body))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode", equalTo("LFP-2003")));
    }

    @Test
    void invalidSignatureReturns401() throws Exception {
        String body = inquiryBody("SIG-BAD");

        mockMvc.perform(post("/api/inquiries")
                        .header("X-API-Key", BANK_KEY)
                        .header("X-Timestamp", String.valueOf(Instant.now().getEpochSecond()))
                        .header("X-Request-Signature", "bad-signature")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode", equalTo("LFP-2003")));
    }

    @Test
    void validSignaturePasses() throws Exception {
        String body = inquiryBody("SIG-VALID");
        String timestamp = String.valueOf(Instant.now().getEpochSecond());

        mockMvc.perform(post("/api/inquiries")
                        .header("X-API-Key", BANK_KEY)
                        .header("X-Timestamp", timestamp)
                        .header("X-Request-Signature", signature("POST", "/api/inquiries", null, timestamp, body))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", equalTo("ELIGIBLE")));
    }

    @Test
    void protectedAdminPostAlsoRequiresSignature() throws Exception {
        mockMvc.perform(post("/api/routing-rules/cache/clear")
                        .header("X-API-Key", "sk-admin-switching-2026"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode", equalTo("LFP-2003")));
    }

    @Test
    void protectedPatchAlsoRequiresSignature() throws Exception {
        String body = """
                {
                  "enabled": true
                }
                """;

        mockMvc.perform(patch("/api/connector-configs/MOCK_BANK_B_CONNECTOR")
                        .header("X-API-Key", "sk-admin-switching-2026")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode", equalTo("LFP-2003")));
    }

    @Test
    void oauthTokenEndpointDoesNotRequireRequestSignature() throws Exception {
        mockMvc.perform(post("/v1/oauth/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content("grant_type=client_credentials&client_id=client-bank-a&client_secret=wrong"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode", equalTo("LFP-2001")));
    }

    private String signature(String method, String path, String query, String timestamp, String body) {
        return HmacSignatureVerifier.signHex(
                HmacSignatureVerifier.canonicalString(method, path, query, timestamp, body),
                BANK_KEY);
    }

    private String inquiryBody(String clientInquiryId) {
        return """
                {
                  "clientInquiryId": "%s",
                  "sourceBank": "BANK_A",
                  "destinationBank": "BANK_B",
                  "creditorAccount": "020123456789",
                  "amount": 1000.00,
                  "currency": "LAK",
                  "reference": "signature test"
                }
                """.formatted(clientInquiryId);
    }
}
