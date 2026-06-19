package com.example.switching.operations.service;

import com.example.switching.AbstractIntegrationTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class OperationsTransferTraceIntegrationTest extends AbstractIntegrationTest {

    private static final AtomicInteger SUFFIX_SEQUENCE = new AtomicInteger(8000);

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
    void operationsTransferTraceReturnsCombinedTimeline() throws Exception {
        String suffix = uniqueSuffix();
        String transferRef = "TRX-TRACE-" + suffix;
        String inquiryRef = "INQ-TRACE-" + suffix;

        insertTransfer(transferRef, inquiryRef);
        insertIsoInquiry(inquiryRef, transferRef);
        insertOutboxEvent(transferRef);
        insertIsoMessages(transferRef, inquiryRef);
        insertAuditLogs(transferRef, inquiryRef);

        MvcResult result = mockMvc.perform(
                        get("/api/operations/transfers/{transferRef}/trace", transferRef)
                )
                .andReturn();

        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertEquals(
                200,
                result.getResponse().getStatus(),
                "Expected trace endpoint to return HTTP 200. Response body: " + body
        );

        JsonNode root = objectMapper.readTree(body);

        assertTrue(
                root.path("status").asText().startsWith("TRACE_FOUND"),
                "Expected TRACE_FOUND or TRACE_FOUND_WITH_WARNINGS. Body: " + body
        );
        assertEquals(transferRef, root.path("transferRef").asText());
        assertEquals(inquiryRef, root.path("inquiryRef").asText());
        assertEquals("SUCCESS", root.path("currentStatus").asText());

        assertTrue(root.path("summary").path("timelineEventCount").asInt() >= 2);
        assertTrue(root.path("summary").path("hasInquiry").asBoolean());
        assertTrue(root.path("summary").path("transferSuccessful").asBoolean());

        assertEquals(transferRef, root.path("transfer").path("transferRef").asText());
        assertEquals("BANK_A", root.path("transfer").path("sourceBank").asText());
        assertEquals("BANK_B", root.path("transfer").path("destinationBank").asText());
        assertEquals("********0001", root.path("transfer").path("debtorAccount").asText());
        assertEquals("********0001", root.path("transfer").path("creditorAccount").asText());
        assertEquals(
                "/api/operations/transfers/" + transferRef + "/trace",
                root.path("transfer").path("operationTraceApiPath").asText()
        );

        assertEquals(inquiryRef, root.path("inquiry").path("inquiryRef").asText());
        assertEquals("USED", root.path("inquiry").path("status").asText());
        assertEquals("********0001", root.path("inquiry").path("creditorAccount").asText());

        assertTrue(root.path("timeline").isArray(), "Expected timeline array. Body: " + body);
        assertFalse(root.path("timeline").isEmpty(), "Expected non-empty timeline. Body: " + body);

        boolean hasTransferCreated = false;
        boolean hasInquiryCreated = false;

        for (JsonNode item : root.path("timeline")) {
            String eventType = item.path("eventType").asText();

            if ("TRANSFER_CREATED".equals(eventType)) {
                hasTransferCreated = true;
            }

            if ("ISO_INQUIRY_CREATED".equals(eventType)) {
                hasInquiryCreated = true;
            }
        }

        assertTrue(hasTransferCreated, "timeline should include TRANSFER_CREATED. Body: " + body);
        assertTrue(hasInquiryCreated, "timeline should include ISO_INQUIRY_CREATED. Body: " + body);
    }

    @Test
    void operationsTransferTraceReturns404WhenTransferRefIsMissing() throws Exception {
        MvcResult result = mockMvc.perform(
                        get("/api/operations/transfers/{transferRef}/trace", "TRX-TRACE-NOT-FOUND")
                )
                .andReturn();

        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertEquals(
                404,
                result.getResponse().getStatus(),
                "Expected missing transfer trace to return 404. Response body: " + body
        );
    }

    private void insertTransfer(String transferRef, String inquiryRef) {
        LocalDateTime now = LocalDateTime.now();
        String suffix = transferRef.replace("TRX-TRACE-", "");

        jdbcTemplate.update(
                """
                INSERT INTO transactions (
                    transaction_ref,
                    client_transaction_id,
                    idempotency_key,
                    source_bank,
                    source_account_no,
                    destination_bank,
                    destination_account_no,
                    destination_account_name,
                    amount,
                    currency,
                    channel_id,
                    route_code,
                    connector_name,
                    external_reference,
                    status,
                    error_code,
                    error_message,
                    reference,
                    inquiry_ref,
                    business_date,
                    created_at,
                    updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_DATE, ?, ?)
                """,
                transferRef,
                "CLIENT-" + suffix,
                "TRACE-IDEMP-" + suffix,
                "BANK_A",
                "010100000001",
                "BANK_B",
                "020200000001",
                "ISO INBOUND RECEIVER",
                new BigDecimal("150000.00"),
                "LAK",
                "ISO20022_XML",
                "ROUTE_BANK_A_TO_BANK_B_PACS008",
                "MOCK_BANK_B_CONNECTOR",
                "MOCK-TRACE-" + suffix,
                "SUCCESS",
                null,
                null,
                "Operations trace test",
                inquiryRef,
                now.minusSeconds(8),
                now.minusSeconds(1)
        );
    }

    private void insertIsoInquiry(String inquiryRef, String transferRef) {
        LocalDateTime now = LocalDateTime.now();

        jdbcTemplate.update(
                """
                INSERT INTO inquiries (
                    inquiry_ref,
                    channel_id,
                    message_id,
                    instruction_id,
                    end_to_end_id,
                    source_bank,
                    destination_bank,
                    debtor_account,
                    creditor_account,
                    amount,
                    currency,
                    reference,
                    status,
                    account_found,
                    bank_available,
                    eligible_for_transfer,
                    error_code,
                    error_message,
                    expires_at,
                    used_by_transaction_ref,
                    business_date,
                    created_at,
                    updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, COALESCE(?, 'LAK'), ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_DATE, ?, ?)
                """,
                inquiryRef,
                "ISO20022_XML",
                "ACMT023-" + inquiryRef,
                "VERIFY-" + inquiryRef,
                null,
                "BANK_A",
                "BANK_B",
                null,
                "020200000001",
                null,
                null,
                "Operations trace inquiry",
                "USED",
                true,
                true,
                true,
                null,
                null,
                now.plusMinutes(15),
                transferRef,
                now.minusSeconds(10),
                now.minusSeconds(1)
        );
    }

    private void insertOutboxEvent(String transferRef) {
        LocalDateTime now = LocalDateTime.now();

        jdbcTemplate.update(
                """
                INSERT INTO outbox_messages (
                    transaction_ref,
                    message_type,
                    payload,
                    status,
                    retry_count,
                    last_error,
                    created_at,
                    updated_at,
                    processed_at,
                    next_retry_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                transferRef,
                "PACS_008",
                "{\"transferRef\":\"" + transferRef + "\"}",
                "SUCCESS",
                0,
                null,
                now.minusSeconds(7),
                now.minusSeconds(2),
                now.minusSeconds(2),
                null
        );
    }

    private void insertIsoMessages(String transferRef, String inquiryRef) {
        LocalDateTime now = LocalDateTime.now();

        jdbcTemplate.update(
                """
                INSERT INTO iso_messages (
                    correlation_ref,
                    inquiry_ref,
                    transaction_ref,
                    end_to_end_id,
                    message_id,
                    message_type,
                    direction,
                    security_status,
                    validation_status,
                    error_code,
                    error_message,
                    business_date,
                    created_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_DATE, ?)
                """,
                transferRef,
                inquiryRef,
                transferRef,
                "E2E-" + transferRef,
                "PACS008-" + transferRef,
                "PACS_008",
                "INBOUND",
                "ENCRYPTED",
                "VALID",
                null,
                null,
                now.minusSeconds(6)
        );

        jdbcTemplate.update(
                """
                INSERT INTO iso_messages (
                    correlation_ref,
                    inquiry_ref,
                    transaction_ref,
                    end_to_end_id,
                    message_id,
                    message_type,
                    direction,
                    security_status,
                    validation_status,
                    error_code,
                    error_message,
                    business_date,
                    created_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_DATE, ?)
                """,
                transferRef,
                inquiryRef,
                transferRef,
                "E2E-" + transferRef,
                "PACS002-" + transferRef,
                "PACS_002",
                "OUTBOUND",
                "ENCRYPTED",
                "VALID",
                null,
                null,
                now.minusSeconds(3)
        );
    }

    private void insertAuditLogs(String transferRef, String inquiryRef) {
        LocalDateTime now = LocalDateTime.now();

        jdbcTemplate.update(
                """
                INSERT INTO audit_logs (
                    event_type,
                    reference_type,
                    reference_id,
                    actor,
                    payload,
                    created_at
                )
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                "TRANSFER_CREATED",
                "TRANSFER",
                transferRef,
                "TEST",
                "{\"transferRef\":\"" + transferRef + "\"}",
                now.minusSeconds(8)
        );

        jdbcTemplate.update(
                """
                INSERT INTO audit_logs (
                    event_type,
                    reference_type,
                    reference_id,
                    actor,
                    payload,
                    created_at
                )
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                "ISO_INQUIRY_USED_BY_TRANSFER",
                "ISO_INQUIRY",
                inquiryRef,
                "TEST",
                "{\"inquiryRef\":\"" + inquiryRef + "\",\"transferRef\":\"" + transferRef + "\"}",
                now.minusSeconds(1)
        );
    }

    private String uniqueSuffix() {
        int sequence = SUFFIX_SEQUENCE.incrementAndGet();
        long millisPart = Math.floorMod(System.currentTimeMillis(), 10_000L);
        return millisPart + "-" + sequence;
    }
}
