package com.example.switching.dispute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.example.switching.AbstractIntegrationTest;
import com.example.switching.webhook.WebhookTestSecrets;
import com.example.switching.dispute.dto.DisputeRaiseRequest;
import com.example.switching.dispute.dto.DisputeRaiseResponse;
import com.example.switching.dispute.dto.DisputeResponse;
import com.example.switching.dispute.exception.DisputeNotAuthorizedException;
import com.example.switching.dispute.service.DisputeRaiseService;
import com.example.switching.dispute.service.DisputeResolutionService;

/**
 * Integration tests for dispute resolution (TC-DISPUTE-RES-001 – 003).
 */
class DisputeResolutionIntegrationTest extends AbstractIntegrationTest {

    @Autowired DisputeRaiseService      raiseService;
    @Autowired DisputeResolutionService resolutionService;
    @Autowired JdbcTemplate             jdbcTemplate;

    // ── TC-DISPUTE-RES-001 ───────────────────────────────────────────────────

    @Test
    void respond_byRespondingPsp_movesToUnderReview() {
        seedPool("BANK_B", new BigDecimal("500000.00"));
        String txnRef = seedSettledTxn("BANK_A", "BANK_B");

        DisputeRaiseResponse raised = raiseService.raise(
                new DisputeRaiseRequest(txnRef, "WRONG_AMOUNT", "BANK_A", null, null));

        // BANK_B responds
        DisputeResponse resp = resolutionService.respond(
                raised.disputeId(), "BANK_B", "[\"evidence1.pdf\"]");

        assertEquals("UNDER_REVIEW", resp.status());
        assertNotNull(resp.evidence(), "Evidence must be stored");
    }

    // ── TC-DISPUTE-RES-002 ───────────────────────────────────────────────────

    @Test
    void resolve_refund_createsRefundTransactionAndWebhook() {
        seedPool("BANK_B", new BigDecimal("500000.00"));
        seedWebhookRegistration("BANK_A", "DISPUTE.REFUND.COMPLETED");
        String txnRef = seedSettledTxn("BANK_A", "BANK_B");

        DisputeRaiseResponse raised = raiseService.raise(
                new DisputeRaiseRequest(txnRef, "NOT_RECEIVED", "BANK_A", null, null));

        // Resolve with REFUND decision
        DisputeResponse resolved = resolutionService.resolve(
                raised.disputeId(), "BANK_A", "REFUND", "Agreed to refund", false);

        assertEquals("RESOLVED_REFUND", resolved.status());
        assertNotNull(resolved.resolvedAt());

        // Verify refund_transactions COMPLETED row
        Integer refundCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM refund_transactions WHERE dispute_id = ? AND status = 'COMPLETED'",
                Integer.class, raised.disputeId());
        assertEquals(1, refundCount, "refund_transactions COMPLETED row must exist");

        // Verify SETTLED reversal transaction created
        Integer txnCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions t JOIN refund_transactions rt ON rt.refund_txn_ref = t.transaction_ref WHERE rt.dispute_id = ? AND t.status = 'SETTLED'",
                Integer.class, raised.disputeId());
        assertEquals(1, txnCount, "SETTLED reversal transaction must exist");
    }

    // ── TC-DISPUTE-RES-003 ───────────────────────────────────────────────────

    @Test
    void respond_unauthorizedPsp_throwsDisputeNotAuthorized() {
        String txnRef = seedSettledTxn("BANK_A", "BANK_B");

        DisputeRaiseResponse raised = raiseService.raise(
                new DisputeRaiseRequest(txnRef, "FRAUD", "BANK_A", null, null));

        // BANK_C is not party to this dispute
        assertThrows(DisputeNotAuthorizedException.class,
                () -> resolutionService.respond(raised.disputeId(), "BANK_C", "[]"),
                "Non-party PSP must throw DisputeNotAuthorizedException (LFP-9004)");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private String seedSettledTxn(String src, String dst) {
        String txnRef = "TXN-RES-" + System.nanoTime();
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
                ) VALUES (?,?,?,'BANK_A','ACC_SRC','BANK_B','ACC_DST','Test Merchant',
                    150000.00,'LAK','DISPUTE_TEST','ROUTE_A','CONN_A',
                    'SETTLED',?,?,'DNS',false,?,?,?,?)
                """,
                txnRef, txnRef, "FLOW-RES", txnRef, txnRef,
                LocalDate.now(), now, now, now);
        return txnRef;
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
