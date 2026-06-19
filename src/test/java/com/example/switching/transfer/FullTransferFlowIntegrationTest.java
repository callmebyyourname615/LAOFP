package com.example.switching.transfer;

import com.example.switching.AbstractIntegrationTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;
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

import com.example.switching.outbox.service.OutboxProcessorService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class FullTransferFlowIntegrationTest extends AbstractIntegrationTest {

    private static final String SOURCE_BANK      = "BANK_A";
    private static final String DESTINATION_BANK = "BANK_B";
    private static final String DEBTOR_ACCOUNT   = "010100000001";
    private static final String CREDITOR_ACCOUNT = "020200000001";
    private static final BigDecimal AMOUNT       = new BigDecimal("150000.00");
    private static final String CURRENCY         = "LAK";
    private static final String CONNECTOR_NAME   = "MOCK_BANK_B_CONNECTOR";

    private static final AtomicInteger SEQ = new AtomicInteger(50_000);

    @Autowired private WebApplicationContext wac;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private OutboxProcessorService outboxProcessorService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac).build();
        seedFixtures();
    }

    // ── Section 1: Inquiry Validation ────────────────────────────────────────

    @Test
    void inquiry_missingSourceBank_returns400() throws Exception {
        mockMvc.perform(post("/api/inquiries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "destinationBank": "BANK_B",
                                  "creditorAccount": "020200000001",
                                  "amount": 150000.00,
                                  "currency": "LAK"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void inquiry_missingDestinationBank_returns400() throws Exception {
        mockMvc.perform(post("/api/inquiries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceBank": "BANK_A",
                                  "creditorAccount": "020200000001",
                                  "amount": 150000.00,
                                  "currency": "LAK"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void inquiry_missingCreditorAccount_returns400() throws Exception {
        mockMvc.perform(post("/api/inquiries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceBank": "BANK_A",
                                  "destinationBank": "BANK_B",
                                  "amount": 150000.00,
                                  "currency": "LAK"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void inquiry_unknownSourceBank_returns4xx() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/inquiries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceBank": "BANK_UNKNOWN_XYZ",
                                  "destinationBank": "BANK_B",
                                  "creditorAccount": "020200000001",
                                  "amount": 150000.00,
                                  "currency": "LAK"
                                }
                                """))
                .andReturn();

        int status = result.getResponse().getStatus();
        assertTrue(status >= 400, "unknown sourceBank must return 4xx, got " + status);
    }

    @Test
    void inquiry_validRequest_returnsEligible() throws Exception {
        String suffix = suffix();

        MvcResult result = mockMvc.perform(post("/api/inquiries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(inquiryBody(suffix)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = parse(result);
        String inquiryRef = json.path("inquiryRef").asText();

        assertNotNull(inquiryRef);
        assertTrue(inquiryRef.startsWith("INQ-"), "inquiryRef must start with INQ-");
        assertEquals("ELIGIBLE", json.path("status").asText());
        assertTrue(json.path("eligibleForTransfer").asBoolean());
        assertTrue(json.path("bankAvailable").asBoolean());
        assertTrue(json.path("accountFound").asBoolean());
    }

    @Test
    void inquiry_getByRef_returns200() throws Exception {
        String suffix = suffix();
        String inquiryRef = createInquiry(suffix);

        MvcResult result = mockMvc.perform(get("/api/inquiries/{ref}", inquiryRef))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = parse(result);
        assertEquals(inquiryRef, json.path("inquiryRef").asText());
    }

    @Test
    void inquiry_getByRef_notFound_returns404() throws Exception {
        mockMvc.perform(get("/api/inquiries/{ref}", "INQ-NOT-EXIST-" + suffix()))
                .andExpect(status().isNotFound());
    }

    // ── Section 2: Transfer — Validation ─────────────────────────────────────

    @Test
    void transfer_missingInquiryRef_returns4xx() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceBank": "BANK_A",
                                  "destinationBank": "BANK_B",
                                  "debtorAccount": "010100000001",
                                  "creditorAccount": "020200000001",
                                  "amount": 150000.00,
                                  "currency": "LAK"
                                }
                                """))
                .andReturn();

        int status = result.getResponse().getStatus();
        assertTrue(status >= 400, "missing inquiryRef must return 4xx, got " + status);
    }

    @Test
    void transfer_inquiryNotFound_returns4xx() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferBody("INQ-NOT-EXIST-" + suffix(), null)))
                .andReturn();

        int status = result.getResponse().getStatus();
        assertTrue(status >= 400, "non-existent inquiryRef must return 4xx, got " + status);
    }

    @Test
    void transfer_sourceBankMismatch_returns4xx() throws Exception {
        String suffix = suffix();
        String inquiryRef = createInquiry(suffix);

        MvcResult result = mockMvc.perform(post("/api/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "inquiryRef": "%s",
                                  "sourceBank": "BANK_WRONG",
                                  "destinationBank": "BANK_B",
                                  "debtorAccount": "010100000001",
                                  "creditorAccount": "020200000001",
                                  "amount": 150000.00,
                                  "currency": "LAK"
                                }
                                """.formatted(inquiryRef)))
                .andReturn();

        int status = result.getResponse().getStatus();
        assertTrue(status >= 400, "sourceBank mismatch must return 4xx, got " + status);
    }

    @Test
    void transfer_amountMismatch_returns4xx() throws Exception {
        String suffix = suffix();
        String inquiryRef = createInquiry(suffix);

        MvcResult result = mockMvc.perform(post("/api/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "inquiryRef": "%s",
                                  "sourceBank": "BANK_A",
                                  "destinationBank": "BANK_B",
                                  "debtorAccount": "010100000001",
                                  "creditorAccount": "020200000001",
                                  "amount": 999999.00,
                                  "currency": "LAK"
                                }
                                """.formatted(inquiryRef)))
                .andReturn();

        int status = result.getResponse().getStatus();
        assertTrue(status >= 400, "amount mismatch must return 4xx, got " + status);
    }

    @Test
    void transfer_currencyMismatch_returns4xx() throws Exception {
        String suffix = suffix();
        String inquiryRef = createInquiry(suffix);

        MvcResult result = mockMvc.perform(post("/api/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "inquiryRef": "%s",
                                  "sourceBank": "BANK_A",
                                  "destinationBank": "BANK_B",
                                  "debtorAccount": "010100000001",
                                  "creditorAccount": "020200000001",
                                  "amount": 150000.00,
                                  "currency": "USD"
                                }
                                """.formatted(inquiryRef)))
                .andReturn();

        int status = result.getResponse().getStatus();
        assertTrue(status >= 400, "currency mismatch must return 4xx, got " + status);
    }

    // ── Section 3: Transfer — Happy Path ─────────────────────────────────────

    @Test
    void transfer_happyPath_statusIsAcceptedAfterCreate() throws Exception {
        String suffix = suffix();
        String inquiryRef = createInquiry(suffix);

        MvcResult result = mockMvc.perform(post("/api/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferBody(inquiryRef, null)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = parse(result);
        String transferRef = json.path("transferRef").asText();

        assertNotNull(transferRef);
        assertTrue(transferRef.length() > 0);
        assertEquals("ACCEPTED", json.path("status").asText());
    }

    @Test
    void transfer_happyPath_outboxDispatch_reachesSettled() throws Exception {
        String suffix = suffix();
        String inquiryRef = createInquiry(suffix);
        String transferRef = createTransfer(inquiryRef);

        Long outboxId = findOutboxEventId(transferRef);
        assertNotNull(outboxId, "outbox event must be enqueued after transfer creation");

        outboxProcessorService.processSingleEvent(outboxId);

        String finalStatus = transferStatus(transferRef);
            assertEquals("SETTLED", finalStatus,
                "transfer must reach SETTLED after outbox dispatch with mock connector");
        assertEquals(1, countPoolOperation(transferRef, "CONFIRM"),
                "successful dispatch must confirm the prefunded pool hold");
    }

    @Test
    void transfer_getByRef_returns200() throws Exception {
        String suffix = suffix();
        String inquiryRef = createInquiry(suffix);
        String transferRef = createTransfer(inquiryRef);

        MvcResult result = mockMvc.perform(get("/api/transfers/{ref}", transferRef))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = parse(result);
        assertEquals(transferRef, json.path("transferRef").asText());
        assertEquals(SOURCE_BANK, json.path("sourceBank").asText());
        assertEquals(DESTINATION_BANK, json.path("destinationBank").asText());
    }

    @Test
    void transfer_notFound_returns404() throws Exception {
        mockMvc.perform(get("/api/transfers/{ref}", "TRX-NOT-EXIST-" + suffix()))
                .andExpect(status().isNotFound());
    }

    @Test
    void transfer_list_returns200() throws Exception {
        mockMvc.perform(get("/api/transfers"))
                .andExpect(status().isOk());
    }

    // ── Section 4: Inquiry Reuse Prevention ──────────────────────────────────

    @Test
    void transfer_inquiryReuse_secondTransferIsRejected() throws Exception {
        String suffix = suffix();
        String inquiryRef = createInquiry(suffix);

        createTransfer(inquiryRef);

        MvcResult second = mockMvc.perform(post("/api/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferBody(inquiryRef, null)))
                .andReturn();

        int status = second.getResponse().getStatus();
        assertTrue(status == 409 || status == 422,
                "second transfer on same inquiry must be 409 or 422, got " + status);
    }

    // ── Section 5: Idempotency ────────────────────────────────────────────────

    @Test
    void transfer_idempotency_sameKeyReturnsSameTransferRef() throws Exception {
        String suffix = suffix();
        String inquiryRef = createInquiry(suffix);
        String idempotencyKey = "IDEM-" + suffix;

        String ref1 = createTransferWithKey(inquiryRef, idempotencyKey);
        String ref2 = createTransferWithKey(inquiryRef, idempotencyKey);

        assertEquals(ref1, ref2, "same idempotency key must return same transferRef");
    }

    // ── Section 6: Force Reject Path ─────────────────────────────────────────

    @Test
    void transfer_forceReject_transferStatusIsRejected() throws Exception {
        setForceReject(true);
        try {
            String suffix = suffix();
            String inquiryRef = createInquiry(suffix);
            String transferRef = createTransfer(inquiryRef);

            Long outboxId = findOutboxEventId(transferRef);
            assertNotNull(outboxId);
            outboxProcessorService.processSingleEvent(outboxId);

            assertEquals("REJECTED", transferStatus(transferRef),
                    "force-reject connector must set transfer to REJECTED");
            assertEquals(1, countPoolOperation(transferRef, "RELEASE"),
                    "terminal reject must release the prefunded pool hold");

            Map<String, Object> event = jdbcTemplate.queryForMap(
                    "SELECT status, failure_class, will_retry FROM outbox_messages WHERE id = ?",
                    outboxId);
            assertEquals("FAILED", event.get("status"));
            assertEquals("PERMANENT_BUSINESS", event.get("failure_class"));
            assertFalse(toBoolean(event.get("will_retry")),
                    "permanent downstream business rejection must not retry");
        } finally {
            setForceReject(false);
        }
    }

    // ── Section 7: Transfer Trace ─────────────────────────────────────────────

    @Test
    void trace_afterSuccessfulDispatch_returnsCompleteTrace() throws Exception {
        String suffix = suffix();
        String inquiryRef = createInquiry(suffix);
        String transferRef = createTransfer(inquiryRef);

        Long outboxId = findOutboxEventId(transferRef);
        outboxProcessorService.processSingleEvent(outboxId);

        MvcResult result = mockMvc.perform(get("/api/transfers/{ref}/trace", transferRef))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = parse(result);
        assertEquals(transferRef, json.path("transferRef").asText());
    }

    @Test
    void trace_notFound_returns404() throws Exception {
        mockMvc.perform(get("/api/transfers/{ref}/trace", "TRX-NOT-EXIST-" + suffix()))
                .andExpect(status().isNotFound());
    }

    // ── Section 8: Operations Trace ───────────────────────────────────────────

    @Test
    void operationsTrace_afterSuccessfulDispatch_returnsFullTrace() throws Exception {
        String suffix = suffix();
        String inquiryRef = createInquiry(suffix);
        String transferRef = createTransfer(inquiryRef);

        Long outboxId = findOutboxEventId(transferRef);
        outboxProcessorService.processSingleEvent(outboxId);

        MvcResult result = mockMvc.perform(
                        get("/api/operations/transfers/{ref}/trace", transferRef))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = parse(result);
        String traceStatus = json.path("status").asText();
        assertTrue(traceStatus.startsWith("TRACE_FOUND"),
                "expected TRACE_FOUND, got " + traceStatus);
        assertEquals(transferRef, json.path("transferRef").asText());
        assertEquals("SETTLED", json.path("currentStatus").asText());
        assertTrue(json.path("summary").path("transferSuccessful").asBoolean());
    }

    @Test
    void operationsTrace_notFound_returns404() throws Exception {
        mockMvc.perform(get("/api/operations/transfers/{ref}/trace", "TRX-NOT-EXIST-" + suffix()))
                .andExpect(status().isNotFound());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String createInquiry(String suffix) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/inquiries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(inquiryBody(suffix)))
                .andExpect(status().isOk())
                .andReturn();

        return parse(result).path("inquiryRef").asText();
    }

    private String createTransfer(String inquiryRef) throws Exception {
        return createTransferWithKey(inquiryRef, null);
    }

    private String createTransferWithKey(String inquiryRef, String idempotencyKey) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferBody(inquiryRef, idempotencyKey)))
                .andExpect(status().isOk())
                .andReturn();

        return parse(result).path("transferRef").asText();
    }

    private String inquiryBody(String suffix) {
        return """
                {
                  "sourceBank": "%s",
                  "destinationBank": "%s",
                  "creditorAccount": "%s",
                  "amount": %s,
                  "currency": "%s"
                }
                """.formatted(SOURCE_BANK, DESTINATION_BANK, CREDITOR_ACCOUNT, AMOUNT, CURRENCY);
    }

    private String transferBody(String inquiryRef, String idempotencyKey) {
        String idemLine = idempotencyKey != null
                ? "\"idempotencyKey\": \"%s\",".formatted(idempotencyKey)
                : "";
        return """
                {
                  %s
                  "inquiryRef": "%s",
                  "sourceBank": "%s",
                  "destinationBank": "%s",
                  "debtorAccount": "%s",
                  "creditorAccount": "%s",
                  "amount": %s,
                  "currency": "%s"
                }
                """.formatted(
                idemLine, inquiryRef,
                SOURCE_BANK, DESTINATION_BANK,
                DEBTOR_ACCOUNT, CREDITOR_ACCOUNT,
                AMOUNT, CURRENCY);
    }

    private Long findOutboxEventId(String transferRef) {
        return jdbcTemplate.query(
                "SELECT id FROM outbox_messages WHERE transaction_ref = ? ORDER BY id ASC LIMIT 1",
                rs -> rs.next() ? rs.getLong("id") : null,
                transferRef);
    }

    private String transferStatus(String transferRef) {
        return jdbcTemplate.query(
                "SELECT status FROM transactions WHERE transaction_ref = ? LIMIT 1",
                rs -> rs.next() ? rs.getString("status") : null,
                transferRef);
    }

    private boolean toBoolean(Object value) {
        if (value instanceof Boolean b) return b;
        if (value instanceof Number n) return n.intValue() != 0;
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private JsonNode parse(MvcResult result) throws Exception {
        return objectMapper.readTree(
                result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private String suffix() {
        int seq = SEQ.incrementAndGet();
        long ms = Math.floorMod(System.currentTimeMillis(), 100_000L);
        return ms + "-" + seq;
    }

    private void setForceReject(boolean value) {
        jdbcTemplate.update(
                """
                UPDATE connector_configs
                SET force_reject = ?
                WHERE connector_name = ?
                """,
                value, CONNECTOR_NAME);
    }

    private void seedFixtures() {
        LocalDateTime now = LocalDateTime.now();

        // participants
        upsertParticipant(SOURCE_BANK, "Source Test Bank", now);
        upsertParticipant(DESTINATION_BANK, "Receiver Test Bank", now);

        // connector
        jdbcTemplate.update(
                """
                INSERT INTO connector_configs (
                    connector_name, bank_code, connector_type,
                    endpoint_url, timeout_ms, enabled,
                    force_reject, reject_reason_code, reject_reason_message,
                    created_at, updated_at
                ) VALUES (?, ?, 'MOCK', NULL, 5000, TRUE, FALSE, 'AC01', 'Mock rejected', ?, ?)
                ON CONFLICT (connector_name) DO UPDATE SET
                    enabled = TRUE,
                    force_reject = FALSE,
                    updated_at = EXCLUDED.updated_at
                """,
                CONNECTOR_NAME, DESTINATION_BANK, now, now);

        // routing rule
        jdbcTemplate.update(
                """
                DELETE FROM routing_rules
                WHERE source_bank = ?
                  AND destination_bank = ?
                  AND message_type = 'PACS_008'
                """,
                SOURCE_BANK, DESTINATION_BANK);

        jdbcTemplate.update(
                """
                INSERT INTO routing_rules (
                    route_code, source_bank, destination_bank,
                    message_type, connector_name, priority, enabled,
                    created_at, updated_at
                ) VALUES (?, ?, ?, 'PACS_008', ?, 1, TRUE, ?, ?)
                """,
                "ROUTE_A_TO_B_PACS008_TEST",
                SOURCE_BANK, DESTINATION_BANK,
                CONNECTOR_NAME, now, now);

        jdbcTemplate.update(
                """
                UPDATE psp_pools
                   SET balance = 1000000000.0000,
                       held_amount = 0,
                       last_updated_at = ?
                 WHERE psp_id = ?
                """,
                now, SOURCE_BANK);
    }

    private void upsertParticipant(String bankCode, String bankName, LocalDateTime now) {
        jdbcTemplate.update(
                """
                INSERT INTO participants (
                    bank_code, bank_name, status, participant_type,
                    country, currency, created_at, updated_at
                ) VALUES (?, ?, 'ACTIVE', 'DIRECT', 'LA', 'LAK', ?, ?)
                ON CONFLICT (bank_code) DO UPDATE SET
                    bank_name   = EXCLUDED.bank_name,
                    status      = 'ACTIVE',
                    updated_at  = EXCLUDED.updated_at
                """,
                bankCode, bankName, now, now);
    }

    private int countPoolOperation(String transferRef, String operation) {
        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                  FROM pool_transactions
                 WHERE txn_id = ?
                   AND operation = ?
                """,
                Integer.class,
                transferRef,
                operation);
    }
}
