package com.example.switching.outbox;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.example.switching.AbstractIntegrationTest;
import com.example.switching.outbox.entity.OutboxEventEntity;
import com.example.switching.outbox.entity.ReversalLogEntity;
import com.example.switching.outbox.enums.FailureClass;
import com.example.switching.outbox.repository.OutboxEventRepository;
import com.example.switching.outbox.service.OutboxAutoReversalService;
import com.example.switching.transfer.entity.TransferEntity;

/**
 * P10 Step 3 — FPRE auto-reversal integration tests.
 *
 * <p>Tests {@link OutboxAutoReversalService} directly (not through the processor)
 * so each scenario is tightly scoped:
 * <ul>
 *   <li>TC-AR-001: triggerReversal creates a COMPLETED {@code reversal_log} row.</li>
 *   <li>TC-AR-002: Idempotency guard — a second call with the same txnId returns
 *       the existing row without inserting a duplicate.</li>
 *   <li>TC-AR-003: {@code reasonFor()} maps {@code FailureClass} to the correct
 *       reason string.</li>
 *   <li>TC-AR-004: {@code failure_class} is stored correctly in the log row.</li>
 * </ul>
 */
class FpreAutoReversalIntegrationTest extends AbstractIntegrationTest {

    private static final String DEST_BANK = "BANK_AR_DST";

    @Autowired private OutboxAutoReversalService autoReversalService;
    @Autowired private OutboxEventRepository    outboxEventRepository;
    @Autowired private JdbcTemplate             jdbcTemplate;

    // ─────────────────────────────────────────────────────────────────────────
    // TC-AR-001  triggerReversal creates a COMPLETED reversal_log row
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void triggerReversal_createsCompletedReversalLogRow() {
        String txnId = "AR-TXN-1-" + System.nanoTime();
        OutboxEventEntity event    = persistEvent(txnId, FailureClass.TRANSIENT);
        TransferEntity    transfer = buildTransfer(txnId, DEST_BANK);

        ReversalLogEntity result = autoReversalService.triggerReversal(event, transfer, "MAX_RETRIES");

        assertNotNull(result.getReversalId(), "reversalId must be assigned by the DB");
        assertEquals(txnId,        result.getOriginalTxnId());
        assertEquals(DEST_BANK,    result.getDestinationBank());
        assertEquals("MAX_RETRIES", result.getReason());
        assertEquals("COMPLETED",  result.getStatus());
        assertNotNull(result.getTriggeredAt());
        assertNotNull(result.getCompletedAt(), "Mock implementation must set completedAt immediately");

        // Confirm the DB row exists with the correct status
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM reversal_log WHERE original_txn_id = ?", txnId);
        assertEquals(1, rows.size(), "Exactly one reversal_log row must be created");
        assertEquals("COMPLETED", rows.get(0).get("status"));
        assertEquals(DEST_BANK,   rows.get(0).get("destination_bank"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TC-AR-002  Idempotency guard — second call returns existing row, no dupe
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void triggerReversal_idempotencyGuard_noDuplicateRow() {
        String txnId = "AR-TXN-2-" + System.nanoTime();
        OutboxEventEntity event    = persistEvent(txnId, FailureClass.TRANSIENT);
        TransferEntity    transfer = buildTransfer(txnId, DEST_BANK);

        ReversalLogEntity first  = autoReversalService.triggerReversal(event, transfer, "MAX_RETRIES");
        ReversalLogEntity second = autoReversalService.triggerReversal(event, transfer, "MAX_RETRIES");

        assertEquals(first.getReversalId(), second.getReversalId(),
                "Second call must return the same row (idempotent)");

        long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM reversal_log WHERE original_txn_id = ?", Long.class, txnId);
        assertEquals(1L, count, "Exactly one reversal_log row must exist despite two service calls");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TC-AR-003  reasonFor() maps FailureClass to the correct reason string
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void reasonFor_mapsFailureClassToCorrectString() {
        assertEquals("COMPLIANCE_BLOCK",
                OutboxAutoReversalService.reasonFor(FailureClass.PERMANENT_COMPLIANCE),
                "PERMANENT_COMPLIANCE must map to COMPLIANCE_BLOCK");
        assertEquals("MAX_RETRIES",
                OutboxAutoReversalService.reasonFor(FailureClass.TRANSIENT),
                "TRANSIENT must map to MAX_RETRIES");
        assertEquals("MAX_RETRIES",
                OutboxAutoReversalService.reasonFor(FailureClass.PERMANENT_BUSINESS),
                "PERMANENT_BUSINESS must map to MAX_RETRIES");
        assertEquals("MAX_RETRIES",
                OutboxAutoReversalService.reasonFor(FailureClass.AMBIGUOUS),
                "AMBIGUOUS must map to MAX_RETRIES");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TC-AR-004  failure_class column is stored in the log row
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void triggerReversal_storesFailureClassInLogRow() {
        String txnId = "AR-TXN-4-" + System.nanoTime();
        OutboxEventEntity event    = persistEvent(txnId, FailureClass.PERMANENT_COMPLIANCE);
        TransferEntity    transfer = buildTransfer(txnId, DEST_BANK);

        autoReversalService.triggerReversal(event, transfer, "COMPLIANCE_BLOCK");

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT failure_class, reason FROM reversal_log WHERE original_txn_id = ?", txnId);
        assertEquals("PERMANENT_COMPLIANCE", row.get("failure_class"));
        assertEquals("COMPLIANCE_BLOCK",     row.get("reason"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Insert a minimal outbox_messages row and return the loaded entity so that
     * {@code event.getId()} is the DB-assigned value (required by the audit log).
     */
    private OutboxEventEntity persistEvent(String transferRef, FailureClass failureClass) {
        jdbcTemplate.update("""
                INSERT INTO outbox_messages (transaction_ref, status, message_type, payload, retry_count, created_at)
                VALUES (?, 'FAILED', 'PACS_008', '{}', 5, ?)
                """, transferRef, LocalDateTime.now());

        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM outbox_messages WHERE transaction_ref = ? ORDER BY id DESC LIMIT 1",
                Long.class, transferRef);

        OutboxEventEntity event = outboxEventRepository.findById(id).orElseThrow();
        event.setFailureClass(failureClass);   // in-memory only; service reads it, no re-save needed
        return event;
    }

    /** Build a minimal in-memory TransferEntity (no DB row needed for auto-reversal). */
    private TransferEntity buildTransfer(String transferRef, String destinationBank) {
        TransferEntity t = new TransferEntity();
        t.setTransferRef(transferRef);
        t.setDestinationBank(destinationBank);
        return t;
    }
}
