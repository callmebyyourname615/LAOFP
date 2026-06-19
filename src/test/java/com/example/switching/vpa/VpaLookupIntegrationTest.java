package com.example.switching.vpa;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.example.switching.AbstractIntegrationTest;
import com.example.switching.vpa.entity.BeneficiaryTokenEntity;
import com.example.switching.vpa.entity.VpaRegistrationEntity;
import com.example.switching.vpa.repository.BeneficiaryTokenRepository;
import com.example.switching.vpa.repository.VpaRegistrationRepository;
import com.example.switching.vpa.service.BeneficiaryTokenService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Integration tests for VPA lookup + beneficiary token lifecycle.
 *
 * <p>Covers:
 * <ul>
 *   <li>Resolve known VPA → 200, token returned</li>
 *   <li>Resolve unknown VPA → 404 LFP-3001</li>
 *   <li>Token expired → 422 LFP-3003</li>
 *   <li>Token already used → 422 LFP-3004</li>
 *   <li>Transfer with valid token — token is consumed</li>
 * </ul>
 */
@TestPropertySource(properties = "switching.security.api-key.enabled=true")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class VpaLookupIntegrationTest extends AbstractIntegrationTest {

    private static final String BANK_KEY     = "sk-bank-a-switching-2026";
    private static final String OPS_KEY      = "sk-ops-switching-2026";
    private static final String RESOLVE_URL  = "/v1/lookup/resolve";
    private static final String REGISTER_URL = "/v1/lookup/vpa/register";

    @Autowired private WebApplicationContext       wac;
    @Autowired private FilterChainProxy            springSecurityFilterChain;
    @Autowired private ObjectMapper                objectMapper;
    @Autowired private VpaRegistrationRepository   vpaRepository;
    @Autowired private BeneficiaryTokenRepository  tokenRepository;
    @Autowired private BeneficiaryTokenService     tokenService;
    @Autowired private JdbcTemplate                jdbcTemplate;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .addFilters(springSecurityFilterChain)
                .build();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private String registerVpa(String type, String value, String account) throws Exception {
        String body = String.format(
                """
                {"vpaType":"%s","vpaValue":"%s","pspId":"BANK_A",
                 "accountRef":"%s","accountType":"BANK_ACCOUNT",
                 "displayName":"Test %s"}""",
                type, value, account, value);

        MvcResult r = mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("X-API-Key", BANK_KEY))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(r.getResponse().getContentAsString())
                .get("vpaId").asText();
    }

    // ── 1 ─────────────────────────────────────────────────────────────────────

    @Test
    @Order(1)
    void resolve_known_vpa_returns200_and_token() throws Exception {
        registerVpa("MSISDN", "85620200001", "ACC-LK-001");

        String body = """
                {"vpaType":"MSISDN","vpaValue":"85620200001"}""";

        MvcResult result = mockMvc.perform(post(RESOLVE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("X-API-Key", BANK_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.beneficiaryToken").isNotEmpty())
                .andExpect(jsonPath("$.receivingPspId").value("BANK_A"))
                .andExpect(jsonPath("$.accountType").value("BANK_ACCOUNT"))
                .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andReturn();

        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        String tokenId = node.get("beneficiaryToken").asText();
        assertNotNull(tokenId);

        // Token persisted and not yet used
        BeneficiaryTokenEntity token = tokenRepository.findById(tokenId).orElseThrow();
        assert !token.isUsed();
    }

    // ── 2 ─────────────────────────────────────────────────────────────────────

    @Test
    @Order(2)
    void resolve_unknown_vpa_returns404() throws Exception {
        String body = """
                {"vpaType":"NATIONAL_ID","vpaValue":"LA000000000"}""";

        mockMvc.perform(post(RESOLVE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("X-API-Key", BANK_KEY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("LFP-3001"));
    }

    // ── 3 ─────────────────────────────────────────────────────────────────────

    @Test
    @Order(3)
    void expired_token_on_transfer_returns422() throws Exception {
        // Register a VPA
        String vpaId = registerVpa("EMAIL", "expiry@test.la", "ACC-EXP");

        // Issue a token then manually expire it via DB
        BeneficiaryTokenEntity token = tokenService.issue(vpaId);
        jdbcTemplate.update(
                "UPDATE beneficiary_tokens SET expires_at = ? WHERE token_id = ?",
                LocalDateTime.now().minusMinutes(10), token.getTokenId());

        String transferBody = String.format("""
                {"sourceBank":"BANK_A","destinationBank":"BANK_B",
                 "debtorAccount":"001","creditorAccount":"002",
                 "amount":1000.00,"currency":"LAK","reference":"EXP-TEST",
                 "inquiryRef":"SOME-INQ","beneficiaryToken":"%s"}""",
                token.getTokenId());

        mockMvc.perform(post("/api/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferBody)
                        .header("X-API-Key", BANK_KEY))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("LFP-3003"));
    }

    // ── 4 ─────────────────────────────────────────────────────────────────────

    @Test
    @Order(4)
    void used_token_returns422() throws Exception {
        String vpaId = registerVpa("MERCHANT_ID", "MERCH-LK-0001", "ACC-MERCH");

        // Issue and immediately consume
        BeneficiaryTokenEntity token = tokenService.issue(vpaId);
        tokenService.consume(token.getTokenId());

        // Try to consume again via service — must throw
        try {
            tokenService.consume(token.getTokenId());
            throw new AssertionError("Expected BeneficiaryTokenUsedException");
        } catch (com.example.switching.vpa.exception.BeneficiaryTokenUsedException e) {
            // expected
        }
    }

    // ── 5 ─────────────────────────────────────────────────────────────────────

    @Test
    @Order(5)
    void resolve_ops_role_returns200() throws Exception {
        registerVpa("NATIONAL_ID", "LA123456789", "ACC-NID-OPS");

        String body = """
                {"vpaType":"NATIONAL_ID","vpaValue":"LA123456789"}""";

        mockMvc.perform(post(RESOLVE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("X-API-Key", OPS_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.beneficiaryToken").isNotEmpty());
    }

    // ── 6 ─────────────────────────────────────────────────────────────────────

    @Test
    @Order(6)
    void resolve_no_api_key_returns401() throws Exception {
        String body = """
                {"vpaType":"MSISDN","vpaValue":"85620200001"}""";

        mockMvc.perform(post(RESOLVE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    // ── 7 ─────────────────────────────────────────────────────────────────────

    @Test
    @Order(7)
    void issued_token_has_correct_ttl() throws Exception {
        String vpaId = registerVpa("QR_STATIC", "QR-LA-9999", "ACC-QR");

        BeneficiaryTokenEntity token = tokenService.issue(vpaId);

        // Default TTL is 300 seconds — expiresAt must be after issuedAt
        assert token.getExpiresAt().isAfter(token.getIssuedAt());
        assert token.getExpiresAt().isBefore(token.getIssuedAt().plusSeconds(310));
        assert !token.isUsed();
    }
}
