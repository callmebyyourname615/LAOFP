package com.example.switching.outbox;

import com.example.switching.AbstractIntegrationTest;
import com.example.switching.outbox.enums.OutboxStatus;
import com.example.switching.outbox.service.OutboxProcessorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P5 — Concurrent dispatch safety.
 *
 * Verifies that when two threads simultaneously attempt to process the same
 * outbox event exactly one claims it (PENDING → PROCESSING) and the other
 * silently skips it.  No double-dispatch, no duplicate status history rows.
 */
class OutboxConcurrentDispatchIntegrationTest extends AbstractIntegrationTest {

    private static final String SOURCE_BANK      = "BANK_CC_A";
    private static final String DEST_BANK        = "BANK_CC_B";
    private static final String CONNECTOR_NAME   = "MOCK_CC_CONNECTOR";

    @Autowired private OutboxProcessorService outboxProcessorService;
    @Autowired private JdbcTemplate           jdbcTemplate;

    @BeforeEach
    void setUp() {
        seedFixtures();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TC-CC-001  Two threads race to process the same pending event —
    //            only one must claim it; retryCount must be exactly 1.
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void concurrentProcessors_onlyOneClaimsEvent() throws InterruptedException {
        // Arrange: insert a transfer and a PENDING outbox event whose payload
        // carries a null isoMessageId so processing fails with a retryable
        // IllegalStateException (OUT-002).  That guarantees the event ends up
        // back in PENDING (retryCount=1) rather than SUCCESS, making assertion
        // straightforward.
        String transferRef = "CC-TXN-" + System.nanoTime();
        long outboxEventId = insertPendingOutboxEvent(transferRef);

        CountDownLatch startGate = new CountDownLatch(1);
        AtomicInteger claimCount = new AtomicInteger(0); // threads that did real work

        Runnable task = () -> {
            try {
                startGate.await();           // both threads start at the same instant
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            // processSingleEvent returns void; a thread that "claims" the event
            // will advance its state; a thread that skips logs and returns early.
            outboxProcessorService.processSingleEvent(outboxEventId);
            claimCount.incrementAndGet();    // just tracks thread completion, not claim
        };

        ExecutorService pool = Executors.newFixedThreadPool(2);
        pool.submit(task);
        pool.submit(task);
        startGate.countDown(); // release both threads simultaneously
        pool.shutdown();
        assertTrue(pool.awaitTermination(15, TimeUnit.SECONDS), "Threads did not finish");

        // Assert
        Map<String, Object> event = jdbcTemplate.queryForMap(
                "SELECT status, retry_count FROM outbox_messages WHERE id = ?", outboxEventId);

        // The claiming thread failed with a retryable error → event back to PENDING
        // with retryCount incremented exactly once.
        String status     = (String) event.get("status");
        int    retryCount = ((Number) event.get("retry_count")).intValue();

        // Status must be PENDING (retry scheduled) or FAILED (max retries exceeded).
        // With default maxRetry=3, attempt 1 is always retried.
        assertEquals(OutboxStatus.PENDING.name(), status,
                "Event should be re-queued PENDING after one retryable failure");
        assertEquals(1, retryCount,
                "retryCount must be exactly 1 — only one thread must have claimed the event");

        // Verify audit log has exactly one OUTBOX_DISPATCH_STARTED entry for this event.
        int auditClaimCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM audit_logs
                 WHERE event_type = 'OUTBOX_DISPATCH_STARTED'
                   AND reference_id = ?
                """,
                Integer.class, transferRef);

        assertEquals(1, auditClaimCount,
                "Exactly one OUTBOX_DISPATCH_STARTED audit entry expected — no double-claim");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TC-CC-002  A PROCESSING event (mid-flight in another pod) is not
    //            re-claimed by a concurrent processor.
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void processingEvent_isNotReclaimedBySecondThread() throws InterruptedException {
        String transferRef = "CC-TXN-PROC-" + System.nanoTime();
        long outboxEventId = insertPendingOutboxEvent(transferRef);

        // Manually advance status to PROCESSING (simulates another pod already working)
        jdbcTemplate.update(
                "UPDATE outbox_messages SET status = 'PROCESSING' WHERE id = ?", outboxEventId);

        // A second processor should skip it entirely
        outboxProcessorService.processSingleEvent(outboxEventId);

        Map<String, Object> event = jdbcTemplate.queryForMap(
                "SELECT status, retry_count FROM outbox_messages WHERE id = ?", outboxEventId);

        assertEquals("PROCESSING", event.get("status"),
                "Status must remain PROCESSING — second processor must not re-claim");
        Object rc = event.get("retry_count");
        int retryCount = rc == null ? 0 : ((Number) rc).intValue();
        assertEquals(0, retryCount, "retryCount must not be incremented by the skipping thread");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Inserts a minimal transfer row and a PENDING outbox event whose JSON
     * payload intentionally omits isoMessageId (null) so that processing
     * throws a retryable IllegalStateException.
     */
    private long insertPendingOutboxEvent(String transferRef) {
        LocalDateTime now = LocalDateTime.now();

        jdbcTemplate.update(
                """
                INSERT INTO transactions (
                    transaction_ref, client_transaction_id, idempotency_key,
                    source_bank, source_account_no,
                    destination_bank, destination_account_no,
                    amount, currency, channel_id, status, business_date,
                    created_at, updated_at
                ) VALUES (?, ?, ?,  ?, '010100000001', ?, '020200000002',
                          150000.00, 'LAK', 'API', 'ACCEPTED', CURRENT_DATE, ?, ?)
                """,
                transferRef, transferRef, transferRef,
                SOURCE_BANK, DEST_BANK, now, now);

        // Payload with null isoMessageId triggers the "Missing isoMessageId"
        // IllegalStateException path inside OutboxProcessorService, which maps
        // to OUT-002 (retryable).
        // messageType is stored in the outbox_messages.message_type column,
        // NOT inside the JSON payload — DispatchTransferCommand has no such field.
        // isoMessageId=null triggers IllegalStateException → OUT-002 (retryable).
        String payload = """
                {
                  "transferRef": "%s",
                  "isoMessageId": null,
                  "sourceBank": "%s",
                  "destinationBank": "%s",
                  "debtorAccount": "010100000001",
                  "creditorAccount": "020200000002",
                  "amount": 150000.00,
                  "currency": "LAK",
                  "connectorName": "%s",
                  "routeCode": "ROUTE_CC_TEST"
                }
                """.formatted(transferRef, SOURCE_BANK, DEST_BANK, CONNECTOR_NAME);

        jdbcTemplate.update(
                """
                INSERT INTO outbox_messages (
                    transaction_ref, status, message_type, payload,
                    retry_count, created_at, updated_at
                ) VALUES (?, 'PENDING', 'PACS_008', ?, 0, ?, ?)
                """,
                transferRef, payload, now, now);

        return jdbcTemplate.queryForObject(
                "SELECT id FROM outbox_messages WHERE transaction_ref = ? ORDER BY id DESC LIMIT 1",
                Long.class, transferRef);
    }

    private void seedFixtures() {
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
                    bank, bank + " (concurrent test)", now, now);
        }
    }
}
