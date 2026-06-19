package com.example.switching.fpre;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.example.switching.AbstractIntegrationTest;
import com.example.switching.fpre.dto.FpreRetryStatusResponse;
import com.example.switching.fpre.dto.FpreTransferListResponse;
import com.example.switching.fpre.exception.AmbiguousStateException;
import com.example.switching.fpre.service.FpreOperationsService;

class FpreOperationsServiceIntegrationTest extends AbstractIntegrationTest {

    private static final String SOURCE_BANK = "BANK_FP_SRC";
    private static final String DEST_BANK = "BANK_FP_DST";

    @Autowired private FpreOperationsService service;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        LocalDateTime now = LocalDateTime.now();
        for (String bank : List.of(SOURCE_BANK, DEST_BANK)) {
            jdbcTemplate.update("""
                    INSERT INTO participants (bank_code, bank_name, status, participant_type, country, currency, created_at, updated_at)
                    VALUES (?, ?, 'ACTIVE', 'DIRECT', 'LA', 'LAK', ?, ?)
                    ON CONFLICT (bank_code) DO UPDATE SET status = 'ACTIVE', updated_at = EXCLUDED.updated_at
                    """, bank, bank + " (fpre operations test)", now, now);
        }
    }

    @Test
    void retryStatus_returnsLatestOutboxState() {
        String txnId = "FPRE-STATUS-" + System.nanoTime();
        insertTransferAndOutbox(txnId, "PENDING", 2, true);

        FpreRetryStatusResponse response = service.retryStatus(txnId);

        assertEquals(txnId, response.txnId());
        assertEquals("ACCEPTED", response.transferStatus());
        assertEquals("PENDING", response.outboxStatus());
        assertEquals(2, response.attemptCount());
        assertEquals(5, response.maxAttempts());
        assertEquals("TRANSIENT", response.failureClass());
        assertTrue(response.willRetry());
        assertFalse(response.willAutoReverse());
        assertNotNull(response.nextRetryAt());
    }

    @Test
    void retryStatus_throwsAmbiguousStateExceptionForTerminalAmbiguousFailure() {
        String txnId = "FPRE-AMBIGUOUS-" + System.nanoTime();
        insertTransferAndOutbox(txnId, "FAILED", 5, false, null, "AMBIGUOUS");

        assertThrows(AmbiguousStateException.class, () -> service.retryStatus(txnId));
    }

    @Test
    void pendingAndFailedLists_filterByDestinationPsp() {
        String pendingTxn = "FPRE-PENDING-" + System.nanoTime();
        String failedTxn = "FPRE-FAILED-" + System.nanoTime();
        insertTransferAndOutbox(pendingTxn, "PENDING", 1, true);
        insertTransferAndOutbox(failedTxn, "FAILED", 5, false);

        FpreTransferListResponse pending = service.pending(DEST_BANK, 20);
        FpreTransferListResponse failed = service.failed(DEST_BANK, 20);

        assertTrue(pending.items().stream().anyMatch(item -> pendingTxn.equals(item.txnId())));
        assertTrue(failed.items().stream().anyMatch(item -> failedTxn.equals(item.txnId())));
    }

    @Test
    void health_returnsQueueAndSuspensionCounters() {
        String txnId = "FPRE-HEALTH-" + System.nanoTime();
        insertTransferAndOutbox(txnId, "PENDING", 1, true, LocalDateTime.now().minusSeconds(1));

        var health = service.health();

        assertTrue(health.queueDepth() >= 1);
        assertTrue(health.retryableFailureCount() >= 1);
        assertTrue(health.retrySuccessRate() >= 0.0);
    }

    private void insertTransferAndOutbox(String txnId, String outboxStatus, int retryCount, boolean willRetry) {
        insertTransferAndOutbox(txnId, outboxStatus, retryCount, willRetry, null);
    }

    private void insertTransferAndOutbox(String txnId,
                                         String outboxStatus,
                                         int retryCount,
                                         boolean willRetry,
                                         LocalDateTime nextRetryAt) {
        insertTransferAndOutbox(txnId, outboxStatus, retryCount, willRetry, nextRetryAt, "TRANSIENT");
    }

    private void insertTransferAndOutbox(String txnId,
                                         String outboxStatus,
                                         int retryCount,
                                         boolean willRetry,
                                         LocalDateTime nextRetryAt,
                                         String failureClass) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("""
                INSERT INTO transactions (
                    transaction_ref, client_transaction_id, idempotency_key,
                    source_bank, source_account_no,
                    destination_bank, destination_account_no,
                    amount, currency, channel_id, status, business_date,
                    error_code, error_message, created_at, updated_at
                ) VALUES (?, ?, ?, ?, '010100000001', ?, '020200000003',
                          1000.00, 'LAK', 'API', 'ACCEPTED', CURRENT_DATE,
                          'OUT-002', 'retryable failure', ?, ?)
                """, txnId, txnId, txnId, SOURCE_BANK, DEST_BANK, now, now);

        jdbcTemplate.update("""
                INSERT INTO outbox_messages (
                    transaction_ref, status, message_type, payload,
                    retry_count, last_error, failure_class, will_retry,
                    next_retry_at, created_at, updated_at
                ) VALUES (?, ?, 'PACS_008', '{}', ?, 'retryable failure',
                          ?, ?, ?, ?, ?)
                """,
                txnId,
                outboxStatus,
                retryCount,
                failureClass,
                willRetry,
                nextRetryAt != null ? nextRetryAt : (willRetry ? now.plusSeconds(30) : null),
                now,
                now);
    }
}
