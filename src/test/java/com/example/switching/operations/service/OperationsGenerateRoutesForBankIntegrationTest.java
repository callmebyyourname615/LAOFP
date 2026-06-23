package com.example.switching.operations.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.example.switching.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class OperationsGenerateRoutesForBankIntegrationTest extends AbstractIntegrationTest {

    private static final AtomicInteger SUFFIX_SEQUENCE = new AtomicInteger(9000);

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .build();
    }


    @Test
    void generateRoutesForBankCreatesInboundAndOutboundRoutes() throws Exception {
        String suffix = uniqueSuffix();
        String targetBank = "BANK_GEN_" + suffix;
        String peerBank = "BANK_PEER_" + suffix;
        String targetConnector = "MOCK_" + targetBank + "_CONNECTOR";
        String peerConnector = "MOCK_" + peerBank + "_CONNECTOR";

        insertParticipant(targetBank);
        insertParticipant(peerBank);
        insertConnector(targetConnector, targetBank);
        insertConnector(peerConnector, peerBank);

        MvcResult result = mockMvc.perform(post("/api/operations/bank-onboarding/generate-routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bankCode": "%s",
                                  "messageType": "PACS_008",
                                  "mode": "BOTH",
                                  "priority": 3,
                                  "enabled": true
                                }
                                """.formatted(targetBank)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());

        assertEquals(targetBank, root.path("bankCode").asText());
        assertEquals("PACS_008", root.path("messageType").asText());
        assertEquals("BOTH", root.path("mode").asText());
        assertEquals("CREATED", root.path("status").asText());
        assertTrue(root.path("createdCount").asInt() >= 2);

        assertRouteExists(peerBank, targetBank, targetConnector);
        assertRouteExists(targetBank, peerBank, peerConnector);

        Integer auditCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM audit_logs
                WHERE event_type = 'ROUTING_RULES_GENERATED_FOR_BANK'
                  AND reference_id = ?
                """,
                Integer.class,
                targetBank
        );
        assertEquals(1, auditCount);
    }

    @Test
    void generateRoutesForBankSkipsExistingRoutesOnSecondCall() throws Exception {
        String suffix = uniqueSuffix();
        String targetBank = "BANK_SKIP_" + suffix;
        String peerBank = "BANK_SKIP_PEER_" + suffix;

        insertParticipant(targetBank);
        insertParticipant(peerBank);
        insertConnector("MOCK_" + targetBank + "_CONNECTOR", targetBank);
        insertConnector("MOCK_" + peerBank + "_CONNECTOR", peerBank);

        String body = """
                {
                  "bankCode": "%s",
                  "mode": "BOTH"
                }
                """.formatted(targetBank);

        mockMvc.perform(post("/api/operations/bank-onboarding/generate-routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        MvcResult secondResult = mockMvc.perform(post("/api/operations/bank-onboarding/generate-routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = objectMapper.readTree(secondResult.getResponse().getContentAsString());

        assertEquals("SKIPPED", root.path("status").asText());
        assertEquals(0, root.path("createdCount").asInt());
        assertTrue(root.path("skippedCount").asInt() >= 2);
    }

    @Test
    void generateRoutesForBankReturns400WhenBankIsNotActive() throws Exception {
        mockMvc.perform(post("/api/operations/bank-onboarding/generate-routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bankCode": "BANK_DOES_NOT_EXIST"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    private void insertParticipant(String bankCode) {
        jdbcTemplate.update(
                """
                INSERT INTO participants (
                    bank_code,
                    bank_name,
                    status,
                    participant_type,
                    country,
                    currency,
                    created_at,
                    updated_at
                )
                VALUES (?, ?, 'ACTIVE', 'DIRECT', 'LA', 'LAK', NOW(), NOW())
                """,
                bankCode,
                "Generated Route Test " + bankCode
        );
    }

    private void insertConnector(String connectorName, String bankCode) {
        jdbcTemplate.update(
                """
                INSERT INTO connector_configs (
                    connector_name,
                    bank_code,
                    connector_type,
                    endpoint_url,
                    timeout_ms,
                    enabled,
                    force_reject,
                    reject_reason_code,
                    reject_reason_message,
                    created_at,
                    updated_at
                )
                VALUES (?, ?, 'MOCK', NULL, 5000, TRUE, FALSE, 'AC01', 'Mock reject', NOW(), NOW())
                """,
                connectorName,
                bankCode
        );
    }

    private void assertRouteExists(String sourceBank, String destinationBank, String connectorName) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM routing_rules
                WHERE source_bank = ?
                  AND destination_bank = ?
                  AND message_type = 'PACS_008'
                  AND connector_name = ?
                  AND priority = 3
                  AND enabled = TRUE
                """,
                Integer.class,
                sourceBank,
                destinationBank,
                connectorName
        );

        assertEquals(1, count);
    }

    private static String uniqueSuffix() {
        return String.valueOf(SUFFIX_SEQUENCE.incrementAndGet());
    }
}
