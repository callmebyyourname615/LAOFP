package com.example.switching.vpa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.example.switching.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Integration tests for VPA registration CRUD:
 * POST /v1/lookup/vpa/register, PUT /v1/lookup/vpa/{vpaId},
 * DELETE /v1/lookup/vpa/{vpaId}, GET /v1/lookup/vpa/{vpaId}
 */
@TestPropertySource(properties = "switching.security.api-key.enabled=true")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class VpaRegistrationIntegrationTest extends AbstractIntegrationTest {

    private static final String BANK_KEY  = "sk-bank-a-switching-2026";
    private static final String OPS_KEY   = "sk-ops-switching-2026";
    private static final String ADMIN_KEY = "sk-admin-switching-2026";

    private static final String REGISTER_URL = "/v1/lookup/vpa/register";

    @Autowired private WebApplicationContext wac;
    @Autowired private FilterChainProxy      springSecurityFilterChain;
    @Autowired private ObjectMapper          objectMapper;

    private MockMvc mockMvc;

    /** Shared vpaId written by test 1, read by tests 2–5. */
    private static String sharedVpaId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .addFilters(springSecurityFilterChain)
                .build();
    }

    // ── 1 ─────────────────────────────────────────────────────────────────────

    @Test
    @Order(1)
    void register_msisdn_vpa_returns201() throws Exception {
        String body = """
                {"vpaType":"MSISDN","vpaValue":"85620111111","pspId":"BANK_A",
                 "accountRef":"ACC-001","accountType":"BANK_ACCOUNT",
                 "displayName":"Alice MSISDN"}""";

        MvcResult result = mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("X-API-Key", BANK_KEY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.vpaId").isNotEmpty())
                .andExpect(jsonPath("$.vpaType").value("MSISDN"))
                .andExpect(jsonPath("$.vpaValue").value("85620111111"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn();

        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        sharedVpaId = node.get("vpaId").asText();
        assertNotNull(sharedVpaId);
    }

    // ── 2 ─────────────────────────────────────────────────────────────────────

    @Test
    @Order(2)
    void register_duplicate_active_vpa_returns409() throws Exception {
        String body = """
                {"vpaType":"MSISDN","vpaValue":"85620111111","pspId":"BANK_A",
                 "accountRef":"ACC-002","accountType":"BANK_ACCOUNT"}""";

        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("X-API-Key", BANK_KEY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("LFP-3002"));
    }

    // ── 3 ─────────────────────────────────────────────────────────────────────

    @Test
    @Order(3)
    void get_vpa_by_id_returns200() throws Exception {
        assertNotNull(sharedVpaId, "sharedVpaId set by test 1");

        mockMvc.perform(get("/v1/lookup/vpa/{vpaId}", sharedVpaId)
                        .header("X-API-Key", OPS_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vpaId").value(sharedVpaId))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    // ── 4 ─────────────────────────────────────────────────────────────────────

    @Test
    @Order(4)
    void update_vpa_accountRef_returns200() throws Exception {
        assertNotNull(sharedVpaId, "sharedVpaId set by test 1");

        String body = """
                {"accountRef":"ACC-UPDATED","displayName":"Alice Updated"}""";

        mockMvc.perform(put("/v1/lookup/vpa/{vpaId}", sharedVpaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("X-API-Key", BANK_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountRef").value("ACC-UPDATED"))
                .andExpect(jsonPath("$.displayName").value("Alice Updated"));
    }

    // ── 5 ─────────────────────────────────────────────────────────────────────

    @Test
    @Order(5)
    void deregister_vpa_returns204() throws Exception {
        assertNotNull(sharedVpaId, "sharedVpaId set by test 1");

        mockMvc.perform(delete("/v1/lookup/vpa/{vpaId}", sharedVpaId)
                        .header("X-API-Key", BANK_KEY))
                .andExpect(status().isNoContent());

        // VPA should now be INACTIVE
        mockMvc.perform(get("/v1/lookup/vpa/{vpaId}", sharedVpaId)
                        .header("X-API-Key", OPS_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
    }

    // ── 6 ─────────────────────────────────────────────────────────────────────

    @Test
    @Order(6)
    void after_deregister_same_value_can_be_reregistered() throws Exception {
        // Partial unique index only blocks ACTIVE rows; INACTIVE allows re-registration
        String body = """
                {"vpaType":"MSISDN","vpaValue":"85620111111","pspId":"BANK_A",
                 "accountRef":"ACC-RENEW","accountType":"BANK_ACCOUNT"}""";

        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("X-API-Key", BANK_KEY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    // ── 7 ─────────────────────────────────────────────────────────────────────

    @Test
    @Order(7)
    void get_unknown_vpa_returns404() throws Exception {
        mockMvc.perform(get("/v1/lookup/vpa/{vpaId}", "non-existent-vpa-id-99")
                        .header("X-API-Key", OPS_KEY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("LFP-3001"));
    }

    // ── 8 ─────────────────────────────────────────────────────────────────────

    @Test
    @Order(8)
    void register_ops_role_returns403() throws Exception {
        String body = """
                {"vpaType":"NATIONAL_ID","vpaValue":"LA999999999","pspId":"BANK_A",
                 "accountRef":"ACC-NID","accountType":"BANK_ACCOUNT"}""";

        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("X-API-Key", OPS_KEY))
                .andExpect(status().isForbidden());
    }

    // ── 9 ─────────────────────────────────────────────────────────────────────

    @Test
    @Order(9)
    void register_no_api_key_returns401() throws Exception {
        String body = """
                {"vpaType":"EMAIL","vpaValue":"test@bank.la","pspId":"BANK_A",
                 "accountRef":"ACC-EMAIL","accountType":"BANK_ACCOUNT"}""";

        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }
}
