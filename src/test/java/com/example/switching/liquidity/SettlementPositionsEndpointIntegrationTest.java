package com.example.switching.liquidity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.example.switching.AbstractIntegrationTest;
import com.example.switching.settlement.entity.SettlementCycleEntity;
import com.example.switching.settlement.service.SettlementCycleService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Integration tests for {@code GET /v1/settlement/positions}.
 *
 * <p>Verifies HTTP-layer behaviour: security rules, 404 when no OPEN cycle exists,
 * 200 with correct JSON when a cycle is open, and {@code ?cycleRef=} param resolution.
 *
 * <p>Uses future settlement dates (plusDays 55–57) to avoid collision with other test classes.
 */
@TestPropertySource(properties = "switching.security.api-key.enabled=true")
class SettlementPositionsEndpointIntegrationTest extends AbstractIntegrationTest {

    private static final String OPS_KEY   = "sk-ops-switching-2026";
    private static final String ADMIN_KEY = "sk-admin-switching-2026";
    private static final String BANK_KEY  = "sk-bank-a-switching-2026";
    private static final String ENDPOINT  = "/v1/settlement/positions";

    @Autowired private WebApplicationContext webApplicationContext;
    @Autowired private FilterChainProxy springSecurityFilterChain;
    @Autowired private SettlementCycleService cycleService;
    @Autowired private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .addFilters(springSecurityFilterChain)
                .build();
    }

    // ── Test 1 ────────────────────────────────────────────────────────────────

    /**
     * No OPEN settlement cycle exists for any future date → 404.
     * (There may be OPEN cycles from other tests; this test verifies the cycleRef path.)
     * We pass an unknown cycleRef to guarantee a deterministic 404.
     */
    @Test
    void positions_unknownCycleRef_returns404() throws Exception {
        mockMvc.perform(get(ENDPOINT)
                        .param("cycleRef", "SC-99999999-C1")
                        .header("X-API-Key", OPS_KEY))
                .andExpect(status().isNotFound());
    }

    // ── Test 2 ────────────────────────────────────────────────────────────────

    /**
     * OPEN cycle with no transfers batched → 200, empty positions array.
     */
    @Test
    void positions_openCycleNoBatch_returns200EmptyPositions() throws Exception {
        LocalDate sd = LocalDate.now().plusDays(55);
        SettlementCycleEntity cycle = cycleService.openCycle(sd);

        MvcResult result = mockMvc.perform(get(ENDPOINT)
                        .param("cycleRef", cycle.getCycleRef())
                        .header("X-API-Key", OPS_KEY))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(cycle.getCycleRef(), body.get("cycleRef").asText());
        assertEquals("OPEN", body.get("cycleStatus").asText());
        assertNotNull(body.get("settlementDate").asText());
        assertTrue(body.get("positions").isArray());
        assertEquals(0, body.get("positions").size(),
                "No transfers batched → no position rows");
    }

    // ── Test 3 ────────────────────────────────────────────────────────────────

    /**
     * Explicit cycleRef → endpoint resolves by ref, not by latest-OPEN logic.
     * Two cycles opened on different dates; querying the older one returns its data.
     */
    @Test
    void positions_explicitCycleRef_returnsCorrectCycle() throws Exception {
        LocalDate sd1 = LocalDate.now().plusDays(56);
        LocalDate sd2 = LocalDate.now().plusDays(57);
        SettlementCycleEntity cycle1 = cycleService.openCycle(sd1);
        SettlementCycleEntity cycle2 = cycleService.openCycle(sd2);

        // Ask for cycle1 by ref — should not return cycle2's data
        MvcResult result = mockMvc.perform(get(ENDPOINT)
                        .param("cycleRef", cycle1.getCycleRef())
                        .header("X-API-Key", ADMIN_KEY))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(cycle1.getCycleRef(), body.get("cycleRef").asText(),
                "Response must belong to the explicitly requested cycle, not a different one");
        assertEquals(sd1.toString(), body.get("settlementDate").asText());

        // Silence unused variable warning — cycle2 is only needed to prove cycle1 is selected
        assertNotNull(cycle2.getCycleRef());
    }

    // ── Test 4 ────────────────────────────────────────────────────────────────

    /**
     * BANK role must receive 403 Forbidden — net positions are OPS/ADMIN only.
     */
    @Test
    void positions_bankRole_returns403() throws Exception {
        mockMvc.perform(get(ENDPOINT)
                        .header("X-API-Key", BANK_KEY))
                .andExpect(status().isForbidden());
    }

    // ── Test 5 ────────────────────────────────────────────────────────────────

    /**
     * No API key at all → 401 Unauthorized.
     */
    @Test
    void positions_noApiKey_returns401() throws Exception {
        mockMvc.perform(get(ENDPOINT))
                .andExpect(status().isUnauthorized());
    }

    // ── Test 6 ────────────────────────────────────────────────────────────────

    /**
     * ADMIN key can also access the positions endpoint.
     */
    @Test
    void positions_adminRole_returns200OrNotFound() throws Exception {
        // Just verify ADMIN isn't blocked (403) — result may be 200 or 404 depending on OPEN cycles
        mockMvc.perform(get(ENDPOINT)
                        .param("cycleRef", "SC-99999999-C1")
                        .header("X-API-Key", ADMIN_KEY))
                .andExpect(result ->
                        assertTrue(result.getResponse().getStatus() != 403,
                                "ADMIN must not receive 403 on positions endpoint"));
    }
}
