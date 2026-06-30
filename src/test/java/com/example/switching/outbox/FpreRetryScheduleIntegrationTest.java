package com.example.switching.outbox;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.example.switching.AbstractIntegrationTest;
import com.example.switching.outbox.service.OutboxProcessorService;
import com.example.switching.outbox.service.OutboxRetryScheduleService;

/**
 * P10 Step 3 — FPRE push-forward retry schedule verification.
 *
 * <p>Verifies that:
 * <ul>
 *   <li>Each retryable attempt schedules next_retry_at using the FPRE delay table [1,1800,3600].</li>
 *   <li>Attempts 1–3 keep the event in PENDING with will_retry=true.</li>
 *   <li>Attempt 4 (initial failure + 3 retry pushes) transitions the event to FAILED and triggers auto-reversal.</li>
 *   <li>next_retry_at is null after the final attempt.</li>
 * </ul>
 *
 * <p>A null {@code isoMessageId} in the payload produces a reliably TRANSIENT failure
 * (IllegalStateException → OUT-002, retryable=true), driving the full schedule.
 */
class FpreRetryScheduleIntegrationTest extends AbstractIntegrationTest {

    private static final String SOURCE_BANK    = "BANK_FR_A";
    private static final String DEST_BANK      = "BANK_FR_B";
    private static final String CONNECTOR_NAME = "MOCK_FR_CONNECTOR";

    // FPRE base delays in seconds (must match application.yml / FpreProperties defaults)
    private static final int[] BASE_DELAYS = { 1, 1800, 3600 };
    // Allowed clock tolerance during assertions. Default FPRE jitter is disabled.
    private static final int TOLERANCE_SECONDS = 2;

    @Autowired private OutboxProcessorService outboxProcessorService;
    @Autowired private OutboxRetryScheduleService retryScheduleService;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        seedParticipants();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TC-FR-001  Attempt 1 → PENDING, next_retry_at ≈ +1 s
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void attempt1_schedules_1s_delay() {
        long id = insertEvent("FR-TXN-1-" + System.nanoTime(), 0);
        LocalDateTime before = LocalDateTime.now();
        outboxProcessorService.processSingleEvent(id);
        LocalDateTime after = LocalDateTime.now();

        Map<String, Object> row = fetchRow(id);
        assertEquals("PENDING", row.get("status"));
        assertEquals(1, num(row.get("retry_count")));
        assertTrue(bool(row.get("will_retry")));

        assertRetryDelay(before, after, toLdt(row.get("next_retry_at")), BASE_DELAYS[0]);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TC-FR-002  Attempt 2 → PENDING, next_retry_at ≈ +1800 s
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void attempt2_schedules_1800s_delay() {
        long id = insertEvent("FR-TXN-2-" + System.nanoTime(), 1);
        LocalDateTime before = LocalDateTime.now();
        outboxProcessorService.processSingleEvent(id);
        LocalDateTime after = LocalDateTime.now();

        Map<String, Object> row = fetchRow(id);
        assertEquals("PENDING", row.get("status"));
        assertEquals(2, num(row.get("retry_count")));
        assertRetryDelay(before, after, toLdt(row.get("next_retry_at")), BASE_DELAYS[1]);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TC-FR-003  Attempt 3 → PENDING, next_retry_at ≈ +3600 s
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void attempt3_schedules_3600s_delay() {
        long id = insertEvent("FR-TXN-3-" + System.nanoTime(), 2);
        LocalDateTime before = LocalDateTime.now();
        outboxProcessorService.processSingleEvent(id);
        LocalDateTime after = LocalDateTime.now();

        Map<String, Object> row = fetchRow(id);
        assertEquals("PENDING", row.get("status"));
        assertEquals(3, num(row.get("retry_count")));
        assertRetryDelay(before, after, toLdt(row.get("next_retry_at")), BASE_DELAYS[2]);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TC-FR-004  Attempt 4 (final) → FAILED, next_retry_at=null, reversal_log created
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void attempt4_final_setsFailedAndCreatesReversal() {
        String transferRef = "FR-TXN-4-" + System.nanoTime();
        long id = insertEvent(transferRef, 3);

        outboxProcessorService.processSingleEvent(id);

        Map<String, Object> row = fetchRow(id);
        assertEquals("FAILED", row.get("status"), "Final attempt must set FAILED");
        assertEquals(4, num(row.get("retry_count")));
        assertFalse(bool(row.get("will_retry")), "No more retries after attempt 4");
        assertNull(row.get("next_retry_at"), "next_retry_at must be null after final attempt");

        // Verify reversal_log was created
        List<Map<String, Object>> reversals = jdbcTemplate.queryForList(
                "SELECT * FROM reversal_log WHERE original_txn_id = ?", transferRef);
        assertFalse(reversals.isEmpty(), "reversal_log must contain a row for the terminal transfer");
        assertEquals("COMPLETED", reversals.get(0).get("status"),
                "Reversal should be COMPLETED (mock environment)");
        assertEquals("MAX_RETRIES", reversals.get(0).get("reason"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TC-FR-006  OutboxRetryScheduleService.canRetry / isFinalAttempt
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void retryScheduleService_canRetry_and_isFinalAttempt() {
        assertTrue(retryScheduleService.canRetry(1));
        assertTrue(retryScheduleService.canRetry(3));
        assertFalse(retryScheduleService.canRetry(4));
        assertFalse(retryScheduleService.canRetry(5));

        assertFalse(retryScheduleService.isFinalAttempt(1));
        assertFalse(retryScheduleService.isFinalAttempt(3));
        assertTrue(retryScheduleService.isFinalAttempt(4));
        assertTrue(retryScheduleService.isFinalAttempt(5));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TC-FR-007  computeNextRetry returns timestamp within expected range
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void computeNextRetry_allRetryableAttempts_withinExpectedRange() {
        for (int attempt = 1; attempt <= 3; attempt++) {
            LocalDateTime before = LocalDateTime.now();
            LocalDateTime nextRetry = retryScheduleService.computeNextRetry(attempt);
            LocalDateTime after = LocalDateTime.now();

            int base = BASE_DELAYS[attempt - 1];

            assertTrue(nextRetry.isAfter(before.plusSeconds(base - TOLERANCE_SECONDS - 1)),
                    "attempt " + attempt + ": nextRetry too early (base=" + base + "s)");
            assertTrue(nextRetry.isBefore(after.plusSeconds(base + TOLERANCE_SECONDS + 1)),
                    "attempt " + attempt + ": nextRetry too late (base=" + base + "s)");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void assertRetryDelay(LocalDateTime before, LocalDateTime after,
                                   LocalDateTime nextRetry, int baseSeconds) {
        assertNotNull(nextRetry, "next_retry_at must not be null for a retryable failure");
        assertTrue(nextRetry.isAfter(before.plusSeconds(baseSeconds - TOLERANCE_SECONDS - 1)),
                "next_retry_at is earlier than expected (base=" + baseSeconds + "s)");
        assertTrue(nextRetry.isBefore(after.plusSeconds(baseSeconds + TOLERANCE_SECONDS + 1)),
                "next_retry_at is later than expected (base=" + baseSeconds + "s)");
    }

    private long insertEvent(String transferRef, int initialRetryCount) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("""
                INSERT INTO participants (bank_code, bank_name, status, participant_type, country, currency, created_at, updated_at)
                VALUES (?, ?, 'ACTIVE', 'DIRECT', 'LA', 'LAK', ?, ?)
                ON CONFLICT (bank_code) DO UPDATE SET status = 'ACTIVE', updated_at = EXCLUDED.updated_at
                """, DEST_BANK, DEST_BANK + " (fr test)", now, now);

        jdbcTemplate.update("""
                INSERT INTO transactions (
                    transaction_ref, client_transaction_id, idempotency_key,
                    source_bank, source_account_no,
                    destination_bank, destination_account_no,
                    amount, currency, channel_id, status, business_date, created_at, updated_at
                ) VALUES (?, ?, ?, ?, '111100000001', ?, '111200000001',
                          100000.00, 'LAK', 'API', 'ACCEPTED', CURRENT_DATE, ?, ?)
                """, transferRef, transferRef, transferRef, SOURCE_BANK, DEST_BANK, now, now);

        String payload = """
                {"transferRef":"%s","isoMessageId":null,"sourceBank":"%s","destinationBank":"%s",
                 "debtorAccount":"111100000001","creditorAccount":"111200000001",
                 "amount":100000.00,"currency":"LAK","connectorName":"%s","routeCode":"ROUTE_FR_TEST"}
                """.formatted(transferRef, SOURCE_BANK, DEST_BANK, CONNECTOR_NAME);

        jdbcTemplate.update("""
                INSERT INTO outbox_messages (transaction_ref, status, message_type, payload, retry_count, created_at)
                VALUES (?, 'PENDING', 'PACS_008', ?, ?, ?)
                """, transferRef, payload, initialRetryCount, now);

        return jdbcTemplate.queryForObject(
                "SELECT id FROM outbox_messages WHERE transaction_ref = ? ORDER BY id DESC LIMIT 1",
                Long.class, transferRef);
    }

    private void seedParticipants() {
        LocalDateTime now = LocalDateTime.now();
        for (String bank : List.of(SOURCE_BANK, DEST_BANK)) {
            jdbcTemplate.update("""
                    INSERT INTO participants (bank_code, bank_name, status, participant_type, country, currency, created_at, updated_at)
                    VALUES (?, ?, 'ACTIVE', 'DIRECT', 'LA', 'LAK', ?, ?)
                    ON CONFLICT (bank_code) DO UPDATE SET status = 'ACTIVE', updated_at = EXCLUDED.updated_at
                    """, bank, bank + " (fpre test)", now, now);
        }
    }

    private Map<String, Object> fetchRow(long id) {
        return jdbcTemplate.queryForMap(
                "SELECT status, retry_count, next_retry_at, failure_class, will_retry FROM outbox_messages WHERE id = ?", id);
    }

    private int num(Object v) { return ((Number) v).intValue(); }
    private boolean bool(Object v) {
        if (v instanceof Boolean b) return b;
        if (v instanceof Number n) return n.intValue() != 0;
        return Boolean.parseBoolean(String.valueOf(v));
    }
    private LocalDateTime toLdt(Object v) {
        if (v == null) return null;
        if (v instanceof LocalDateTime ldt) return ldt;
        if (v instanceof java.sql.Timestamp ts) return ts.toLocalDateTime();
        throw new IllegalArgumentException("Unexpected type: " + v.getClass());
    }
}
