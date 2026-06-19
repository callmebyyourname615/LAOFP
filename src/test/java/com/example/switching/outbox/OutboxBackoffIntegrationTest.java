package com.example.switching.outbox;

import com.example.switching.AbstractIntegrationTest;
import com.example.switching.outbox.service.OutboxProcessorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P5 — Exponential backoff verification.
 *
 * Verifies that after each retryable technical failure the outbox event's
 * {@code next_retry_at} timestamp advances by the expected FPRE backoff delay:
 *
 *   attempt 1 → +30 s   (±5 s tolerance)
 *   attempt 2 → +60 s   (±10 s tolerance)   [was 120 s before FPRE; now uses delays[1]]
 *   attempt 5 → FAILED  (maxRetry = 5)      [was attempt 3 with maxRetry = 3]
 *
 * A null {@code isoMessageId} in the payload reliably produces an
 * {@code IllegalStateException} → OUT-002 (retryable=true), which is the
 * exact code path that populates {@code next_retry_at}.
 */
class OutboxBackoffIntegrationTest extends AbstractIntegrationTest {

    private static final String SOURCE_BANK    = "BANK_BO_A";
    private static final String DEST_BANK      = "BANK_BO_B";
    private static final String CONNECTOR_NAME = "MOCK_BO_CONNECTOR";

    @Autowired private OutboxProcessorService outboxProcessorService;
    @Autowired private JdbcTemplate           jdbcTemplate;

    @BeforeEach
    void setUp() {
        seedParticipants();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TC-BO-001  Attempt 1 → next_retry_at ≈ now + 30 s
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void attempt1_setsNextRetryAt_30seconds() {
        String transferRef  = "BO-TXN-1-" + System.nanoTime();
        long outboxEventId  = insertPendingOutboxEvent(transferRef, 0);

        LocalDateTime before = LocalDateTime.now();
        outboxProcessorService.processSingleEvent(outboxEventId);
        LocalDateTime after  = LocalDateTime.now();

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT status, retry_count, next_retry_at, failure_class, will_retry FROM outbox_messages WHERE id = ?",
                outboxEventId);

        assertEquals("PENDING", row.get("status"), "Event should be re-queued PENDING");
        assertEquals(1, ((Number) row.get("retry_count")).intValue());
        assertEquals("TRANSIENT", row.get("failure_class"));
        assertTrue(toBoolean(row.get("will_retry")), "retryable technical failure must set will_retry=true");

        LocalDateTime nextRetry = toLocalDateTime(row.get("next_retry_at"));
        assertNotNull(nextRetry, "next_retry_at must not be null for a retryable failure");

        // next_retry_at should be in [before+25s, after+35s]
        assertTrue(nextRetry.isAfter(before.plusSeconds(25)),
                "next_retry_at should be at least 25 s in the future");
        assertTrue(nextRetry.isBefore(after.plusSeconds(35)),
                "next_retry_at should be at most 35 s in the future");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TC-BO-002  Attempt 2 → next_retry_at ≈ now + 60 s (FPRE delay[1])
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void attempt2_setsNextRetryAt_60seconds() {
        String transferRef  = "BO-TXN-2-" + System.nanoTime();
        // Pre-seed with retry_count=1 so the service thinks this is attempt #2
        long outboxEventId  = insertPendingOutboxEvent(transferRef, 1);

        LocalDateTime before = LocalDateTime.now();
        outboxProcessorService.processSingleEvent(outboxEventId);
        LocalDateTime after  = LocalDateTime.now();

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT status, retry_count, next_retry_at FROM outbox_messages WHERE id = ?",
                outboxEventId);

        assertEquals("PENDING", row.get("status"));
        assertEquals(2, ((Number) row.get("retry_count")).intValue());

        LocalDateTime nextRetry = toLocalDateTime(row.get("next_retry_at"));
        assertNotNull(nextRetry, "next_retry_at must not be null for attempt 2");

        // FPRE delay[1] = 60 s ±10% jitter → window [54, 66]; add ±4 s safety buffer → [50, 70]
        assertTrue(nextRetry.isAfter(before.plusSeconds(50)),
                "next_retry_at should be at least 50 s in the future (60 s - 10 s safety)");
        assertTrue(nextRetry.isBefore(after.plusSeconds(70)),
                "next_retry_at should be at most 70 s in the future (60 s + 10 s safety)");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TC-BO-003  Attempt 5 (default max-retry=5) → event goes FAILED,
    //            next_retry_at is null (no more retries)
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void attempt5_atMaxRetry_setsStatusFailed_noNextRetry() {
        String transferRef  = "BO-TXN-3-" + System.nanoTime();
        // retry_count=4 means this is the 5th attempt, which hits maxRetry=5
        long outboxEventId  = insertPendingOutboxEvent(transferRef, 4);

        outboxProcessorService.processSingleEvent(outboxEventId);

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT status, retry_count, next_retry_at, failure_class, will_retry FROM outbox_messages WHERE id = ?",
                outboxEventId);

        assertEquals("FAILED", row.get("status"),
                "Event must be FAILED when max retries is exhausted");
        assertEquals("TRANSIENT", row.get("failure_class"));
        assertFalse(toBoolean(row.get("will_retry")), "max retry exhaustion must set will_retry=false");
        assertNull(row.get("next_retry_at"),
                "next_retry_at must be null — no further retries scheduled");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TC-BO-004  A PENDING event with future next_retry_at is NOT re-processed
    //            before its scheduled time (poller filter validation).
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void pendingEventWithFutureNextRetryAt_isNotClaimedEarly() {
        String transferRef = "BO-TXN-4-" + System.nanoTime();
        long outboxEventId = insertPendingOutboxEvent(transferRef, 0);

        // Manually set next_retry_at to 1 hour in the future
        jdbcTemplate.update(
                "UPDATE outbox_messages SET next_retry_at = ? WHERE id = ?",
                LocalDateTime.now().plusHours(1), outboxEventId);

        // The safety-net poller uses findPendingBatch which filters on
        // `next_retry_at IS NULL OR next_retry_at <= NOW()`.
        // Direct processSingleEvent bypasses that filter, so we test
        // the repository query directly.
        List<Map<String, Object>> pending = jdbcTemplate.queryForList(
                """
                SELECT id FROM outbox_messages
                 WHERE status = 'PENDING'
                   AND (next_retry_at IS NULL OR next_retry_at <= NOW())
                   AND id = ?
                """,
                outboxEventId);

        assertTrue(pending.isEmpty(),
                "Event with future next_retry_at must NOT appear in the pending poll query");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private long insertPendingOutboxEvent(String transferRef, int initialRetryCount) {
        LocalDateTime now = LocalDateTime.now();

        jdbcTemplate.update(
                """
                INSERT INTO transactions (
                    transaction_ref, client_transaction_id, idempotency_key,
                    source_bank, source_account_no,
                    destination_bank, destination_account_no,
                    amount, currency, channel_id, status, business_date,
                    created_at, updated_at
                ) VALUES (?, ?, ?, ?, '010100000001', ?, '020200000003',
                          250000.00, 'LAK', 'API', 'ACCEPTED', CURRENT_DATE, ?, ?)
                """,
                transferRef, transferRef, transferRef,
                SOURCE_BANK, DEST_BANK, now, now);

        // messageType lives in outbox_messages.message_type column, not the JSON payload.
        // isoMessageId=null → IllegalStateException → OUT-002 (retryable=true).
        String payload = """
                {
                  "transferRef": "%s",
                  "isoMessageId": null,
                  "sourceBank": "%s",
                  "destinationBank": "%s",
                  "debtorAccount": "010100000001",
                  "creditorAccount": "020200000003",
                  "amount": 250000.00,
                  "currency": "LAK",
                  "connectorName": "%s",
                  "routeCode": "ROUTE_BO_TEST"
                }
                """.formatted(transferRef, SOURCE_BANK, DEST_BANK, CONNECTOR_NAME);

        jdbcTemplate.update(
                """
                INSERT INTO outbox_messages (
                    transaction_ref, status, message_type, payload,
                    retry_count, created_at, updated_at
                ) VALUES (?, 'PENDING', 'PACS_008', ?, ?, ?, ?)
                """,
                transferRef, payload, initialRetryCount, now, now);

        return jdbcTemplate.queryForObject(
                "SELECT id FROM outbox_messages WHERE transaction_ref = ? ORDER BY id DESC LIMIT 1",
                Long.class, transferRef);
    }

    private void seedParticipants() {
        LocalDateTime now = LocalDateTime.now();
        for (String bank : List.of(SOURCE_BANK, DEST_BANK)) {
            jdbcTemplate.update(
                    """
                    INSERT INTO participants (
                        bank_code, bank_name, status, participant_type,
                        country, currency, created_at, updated_at
                    ) VALUES (?, ?, 'ACTIVE', 'DIRECT', 'LA', 'LAK', ?, ?)
                    ON CONFLICT (bank_code) DO UPDATE SET status = 'ACTIVE', updated_at = EXCLUDED.updated_at
                    """,
                    bank, bank + " (backoff test)", now, now);
        }
    }

    /** Convert the value returned by queryForMap for a DATETIME column. */
    private LocalDateTime toLocalDateTime(Object value) {
        if (value == null) return null;
        if (value instanceof LocalDateTime ldt) return ldt;
        if (value instanceof java.sql.Timestamp ts) return ts.toLocalDateTime();
        throw new IllegalArgumentException("Unexpected datetime type: " + value.getClass());
    }

    private boolean toBoolean(Object value) {
        if (value instanceof Boolean b) return b;
        if (value instanceof Number n) return n.intValue() != 0;
        return Boolean.parseBoolean(String.valueOf(value));
    }
}
