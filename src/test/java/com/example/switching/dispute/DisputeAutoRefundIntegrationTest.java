package com.example.switching.dispute;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.example.switching.AbstractIntegrationTest;
import com.example.switching.webhook.WebhookTestSecrets;
import com.example.switching.dispute.service.DisputeAutoRefundService;

/**
 * Integration tests for dispute auto-refund financial operations (TC-DISPUTE-REFUND-001 – 002).
 */
class DisputeAutoRefundIntegrationTest extends AbstractIntegrationTest {

    @Autowired DisputeAutoRefundService autoRefundService;
    @Autowired JdbcTemplate             jdbcTemplate;

    // ── TC-DISPUTE-REFUND-001 ────────────────────────────────────────────────

    @Test
    void initiateRefund_poolRebalancedAndRefundTransactionCreated() {
        seedPool("BANK_A", new BigDecimal("100000.00"));
        seedPool("BANK_B", new BigDecimal("100000.00"));

        // Seed original SETTLED transaction (BANK_A → BANK_B)
        String txnRef = "TXN-REFUND-" + System.nanoTime();
        seedSettledTxn(txnRef, "BANK_A", "BANK_B", new BigDecimal("40000.00"));

        // Insert a RESOLVED_REFUND dispute pointing to it
        Long disputeId = jdbcTemplate.queryForObject(
                """
                INSERT INTO disputes
                    (txn_ref, raising_psp_id, responding_psp_id, dispute_type, status,
                     raised_at, sla_deadline, created_at, updated_at)
                VALUES (?, 'BANK_A', 'BANK_B', 'WRONG_AMOUNT', 'RESOLVED_REFUND',
                    NOW(), NOW() + INTERVAL '3 days', NOW(), NOW())
                RETURNING dispute_id
                """,
                Long.class, txnRef);

        // Execute refund
        autoRefundService.initiateRefund(disputeId);

        // Verify refund_transactions COMPLETED
        Integer refundCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM refund_transactions WHERE dispute_id = ? AND status = 'COMPLETED'",
                Integer.class, disputeId);
        assertEquals(1, refundCount, "refund_transactions COMPLETED row must exist");

        // Verify SETTLED reversal transaction (BANK_B → BANK_A)
        Integer txnCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM transactions t
                  JOIN refund_transactions rt ON rt.refund_txn_ref = t.transaction_ref
                 WHERE rt.dispute_id = ? AND t.status = 'SETTLED'
                   AND t.source_bank = 'BANK_B' AND t.destination_bank = 'BANK_A'
                """,
                Integer.class, disputeId);
        assertEquals(1, txnCount, "SETTLED reversal transaction (BANK_B→BANK_A) must exist");
    }

    // ── TC-DISPUTE-REFUND-002 ────────────────────────────────────────────────

    @Test
    void initiateRefund_webhookFired() {
        seedPool("BANK_A", new BigDecimal("100000.00"));
        seedPool("BANK_B", new BigDecimal("100000.00"));
        seedWebhookRegistration("BANK_A", "DISPUTE.REFUND.COMPLETED");

        String txnRef = "TXN-REFWH-" + System.nanoTime();
        seedSettledTxn(txnRef, "BANK_A", "BANK_B", new BigDecimal("25000.00"));

        Long disputeId = jdbcTemplate.queryForObject(
                """
                INSERT INTO disputes
                    (txn_ref, raising_psp_id, responding_psp_id, dispute_type, status,
                     raised_at, sla_deadline, created_at, updated_at)
                VALUES (?, 'BANK_A', 'BANK_B', 'NOT_RECEIVED', 'RESOLVED_REFUND',
                    NOW(), NOW() + INTERVAL '1 day', NOW(), NOW())
                RETURNING dispute_id
                """,
                Long.class, txnRef);

        autoRefundService.initiateRefund(disputeId);

        // Get the refund_txn_ref for webhook verification
        String refundTxnRef = jdbcTemplate.queryForObject(
                "SELECT refund_txn_ref FROM refund_transactions WHERE dispute_id = ?",
                String.class, disputeId);

        // Verify DISPUTE.REFUND.COMPLETED webhook delivery log
        Integer wh = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM webhook_delivery_log wdl
                  JOIN webhook_registrations wr ON wr.webhook_id = wdl.webhook_id
                 WHERE wdl.event_type = 'DISPUTE.REFUND.COMPLETED'
                   AND wdl.event_ref  = ?
                   AND wr.psp_id      = 'BANK_A'
                """,
                Integer.class, refundTxnRef);
        assertEquals(1, wh, "DISPUTE.REFUND.COMPLETED webhook must be delivered");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void seedSettledTxn(String txnRef, String src, String dst, BigDecimal amount) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("""
                INSERT INTO transactions (
                    transaction_ref, idempotency_key, flow_ref,
                    source_bank, source_account_no,
                    destination_bank, destination_account_no, destination_account_name,
                    amount, currency, channel_id, route_code, connector_name,
                    status, external_reference, reference,
                    settlement_method, high_value,
                    business_date, accepted_at, settled_at, created_at
                ) VALUES (?,?,?,?,'ACC_SRC',?,'ACC_DST','Refund Test',
                    ?,'LAK','REFUND_TEST','ROUTE_A','CONN_A',
                    'SETTLED',?,?,'DNS',false,?,?,?,?)
                """,
                txnRef, txnRef, "FLOW-REFUND", src, dst, amount,
                txnRef, txnRef, LocalDate.now(), now, now, now);
    }

    private void seedPool(String pspId, BigDecimal balance) {
        jdbcTemplate.update("""
                INSERT INTO psp_pools (psp_id, balance, held_amount, minimum_balance)
                VALUES (?, ?, 0.00, 0.00)
                ON CONFLICT (psp_id) DO UPDATE SET balance = psp_pools.balance + EXCLUDED.balance
                """, pspId, balance);
    }

    private void seedWebhookRegistration(String pspId, String eventType) {
        String webhookId = java.util.UUID.randomUUID().toString();
        var encryptedSecret = WebhookTestSecrets.encrypted();
        jdbcTemplate.update("""
                INSERT INTO webhook_registrations (
                    webhook_id, psp_id, url, secret_ciphertext, secret_key_id,
                    secret_version, secret_hash, event_types, status, created_at, updated_at
                ) VALUES (?, ?, 'http://localhost:19999/noop', ?, ?, ?, ?,
                    ?, 'ACTIVE', NOW(), NOW())
                """,
                webhookId, pspId,
                encryptedSecret.ciphertext(), encryptedSecret.keyId(), encryptedSecret.version(),
                WebhookTestSecrets.sha256(), "[\"" + eventType + "\"]");
    }
}
