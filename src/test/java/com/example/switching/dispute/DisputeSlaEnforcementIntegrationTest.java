package com.example.switching.dispute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.example.switching.AbstractIntegrationTest;
import com.example.switching.dispute.service.DisputeSlaEnforcementService;

/**
 * Integration tests for SLA auto-enforcement (TC-DISPUTE-SLA-001 – 002).
 */
class DisputeSlaEnforcementIntegrationTest extends AbstractIntegrationTest {

    @Autowired DisputeSlaEnforcementService slaService;
    @Autowired JdbcTemplate                 jdbcTemplate;

    // ── TC-DISPUTE-SLA-001 ───────────────────────────────────────────────────

    @Test
    void checkAndEnforceSlAs_overdueDispute_autoRuledAndRefunded() {
        seedPool("BANK_A", new BigDecimal("200000.00"));
        seedPool("BANK_B", new BigDecimal("200000.00"));

        // Seed a SETTLED transaction
        String txnRef = "TXN-SLA-" + System.nanoTime();
        seedSettledTxn(txnRef, "BANK_A", "BANK_B", new BigDecimal("60000.00"));

        // Insert dispute with sla_deadline in the past and status OPEN
        Long disputeId = jdbcTemplate.queryForObject(
                """
                INSERT INTO disputes
                    (txn_ref, raising_psp_id, responding_psp_id, dispute_type, status,
                     raised_at, sla_deadline, created_at, updated_at)
                VALUES (?, 'BANK_A', 'BANK_B', 'NOT_RECEIVED', 'OPEN',
                    NOW() - INTERVAL '3 days', NOW() - INTERVAL '1 minute', NOW(), NOW())
                RETURNING dispute_id
                """,
                Long.class, txnRef);

        // Trigger SLA enforcement
        int resolved = slaService.checkAndEnforceSlAs();

        assertTrue(resolved >= 1, "At least 1 dispute must be auto-resolved");

        // Verify dispute auto_ruled and RESOLVED_REFUND
        Boolean autoRuled = jdbcTemplate.queryForObject(
                "SELECT auto_ruled FROM disputes WHERE dispute_id = ?", Boolean.class, disputeId);
        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM disputes WHERE dispute_id = ?", String.class, disputeId);

        assertEquals(Boolean.TRUE,      autoRuled, "Dispute must be auto_ruled=true");
        assertEquals("RESOLVED_REFUND", status,    "Dispute must be RESOLVED_REFUND");

        // Verify refund_transactions created
        Integer refundCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM refund_transactions WHERE dispute_id = ? AND status = 'COMPLETED'",
                Integer.class, disputeId);
        assertEquals(1, refundCount, "refund_transactions COMPLETED row must exist");
    }

    // ── TC-DISPUTE-SLA-002 ───────────────────────────────────────────────────

    @Test
    void checkAndEnforceSlAs_withinSla_notAutoRuled() {
        String txnRef = "TXN-SLA-OK-" + System.nanoTime();
        seedSettledTxn(txnRef, "BANK_A", "BANK_B", new BigDecimal("10000.00"));

        Long disputeId = jdbcTemplate.queryForObject(
                """
                INSERT INTO disputes
                    (txn_ref, raising_psp_id, responding_psp_id, dispute_type, status,
                     raised_at, sla_deadline, created_at, updated_at)
                VALUES (?, 'BANK_A', 'BANK_B', 'WRONG_AMOUNT', 'OPEN',
                    NOW(), NOW() + INTERVAL '3 days', NOW(), NOW())
                RETURNING dispute_id
                """,
                Long.class, txnRef);

        slaService.checkAndEnforceSlAs();

        // Dispute still OPEN, not auto-ruled
        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM disputes WHERE dispute_id = ?", String.class, disputeId);
        Boolean autoRuled = jdbcTemplate.queryForObject(
                "SELECT auto_ruled FROM disputes WHERE dispute_id = ?", Boolean.class, disputeId);

        assertEquals("OPEN",        status,    "Dispute within SLA must remain OPEN");
        assertEquals(Boolean.FALSE, autoRuled, "Dispute within SLA must not be auto_ruled");
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
                ) VALUES (?,?,?,?,'ACC_SRC',?,'ACC_DST','SLA Test Merchant',
                    ?,'LAK','SLA_TEST','ROUTE_A','CONN_A',
                    'SETTLED',?,?,'DNS',false,?,?,?,?)
                """,
                txnRef, txnRef, "FLOW-SLA", src, dst, amount,
                txnRef, txnRef, LocalDate.now(), now, now, now);
    }

    private void seedPool(String pspId, BigDecimal balance) {
        jdbcTemplate.update("""
                INSERT INTO psp_pools (psp_id, balance, held_amount, minimum_balance)
                VALUES (?, ?, 0.00, 0.00)
                ON CONFLICT (psp_id) DO UPDATE SET balance = psp_pools.balance + EXCLUDED.balance
                """, pspId, balance);
    }
}
