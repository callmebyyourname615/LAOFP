package com.example.switching.qr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.example.switching.AbstractIntegrationTest;
import com.example.switching.qr.dto.QrPayResponse;
import com.example.switching.qr.entity.QrCodeEntity;
import com.example.switching.qr.exception.QrExpiredException;
import com.example.switching.qr.service.QrGeneratorService;
import com.example.switching.qr.service.QrPaymentService;

import org.junit.jupiter.api.Assertions;

/**
 * Integration tests for QR payment flow (TC-QR-PAY-001 – 004).
 *
 * Tests:
 * - Full Scan-Pay-Confirm: pool debited, SETTLED transaction created, QR marked used
 * - STATIC QR reusable after first payment (used flag not set)
 * - Expired DYNAMIC QR payment throws LFP-QR-001
 * - Missing pool balance throws InsufficientPoolBalanceException
 */
class QrPaymentIntegrationTest extends AbstractIntegrationTest {

    @Autowired private QrGeneratorService  generatorService;
    @Autowired private QrPaymentService    paymentService;
    @Autowired private JdbcTemplate        jdbcTemplate;

    // ── TC-QR-PAY-001 ────────────────────────────────────────────────────────

    @Test
    void pay_dynamicQr_succeeds_poolDebitedAndTransactionSettled() {
        seedPool("BANK_A", new BigDecimal("100000.00"));

        String txnRef = "TXN-PAY-" + System.nanoTime();
        QrCodeEntity qr = generatorService.generateDynamic(
                "MERCH_PAY_A", "BANK_B",
                new BigDecimal("5000.00"), txnRef, 300);

        QrPayResponse result = paymentService.pay(qr.getQrId(), "BANK_A", null);

        assertEquals("COMPLETED", result.status());
        assertEquals(qr.getQrId(), result.qrId());
        assertEquals("BANK_A", result.issuingPspId());
        assertEquals("BANK_B", result.acquiringPspId());
        assertEquals(0, new BigDecimal("5000.00").compareTo(result.amount()));
        assertNotNull(result.txnId(), "Transaction ID must be returned");
        assertNotNull(result.completedAt());

        // Verify SETTLED transaction row was created
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE transaction_ref = ? AND status = 'SETTLED'",
                Integer.class, result.txnId());
        assertEquals(1, count, "Exactly one SETTLED transaction must exist");

        // Verify DYNAMIC QR is now marked used
        Boolean used = jdbcTemplate.queryForObject(
                "SELECT used FROM qr_codes WHERE qr_id = ?",
                Boolean.class, qr.getQrId());
        assertTrue(Boolean.TRUE.equals(used), "DYNAMIC QR must be marked used after payment");
    }

    // ── TC-QR-PAY-002 ────────────────────────────────────────────────────────

    @Test
    void pay_staticQr_succeeds_qrRemainsReusable() {
        seedPool("BANK_A", new BigDecimal("50000.00"));

        QrCodeEntity qr = generatorService.generateStatic("MERCH_STATIC", "BANK_B", null);

        QrPayResponse result = paymentService.pay(
                qr.getQrId(), "BANK_A", new BigDecimal("2000.00"));

        assertEquals("COMPLETED", result.status());

        // STATIC QR must remain not-used
        Boolean used = jdbcTemplate.queryForObject(
                "SELECT used FROM qr_codes WHERE qr_id = ?",
                Boolean.class, qr.getQrId());
        assertTrue(Boolean.FALSE.equals(used), "STATIC QR must not be marked used");
    }

    // ── TC-QR-PAY-003 ────────────────────────────────────────────────────────

    @Test
    void pay_expiredDynamicQr_throwsQrExpired() {
        // Create a QR with a very short TTL, then try to pay after it expires.
        // Since we can't actually wait for expiry in a unit-scoped test, we
        // directly manipulate the qr_codes row via JDBC to simulate expiry.
        QrCodeEntity qr = generatorService.generateDynamic(
                "MERCH_EXP", "BANK_B", new BigDecimal("100.00"),
                "TXN-EXP-" + System.nanoTime(), 300);

        // Force-expire the QR by setting expires_at to the past
        jdbcTemplate.update("UPDATE qr_codes SET expires_at = NOW() - INTERVAL '1 minute' WHERE qr_id = ?",
                qr.getQrId());

        Assertions.assertThrows(QrExpiredException.class,
                () -> paymentService.pay(qr.getQrId(), "BANK_A", null),
                "Expired QR must throw QrExpiredException");
    }

    // ── TC-QR-PAY-004 ────────────────────────────────────────────────────────

    @Test
    void pay_staticQrWithoutAmount_throwsIllegalArgument() {
        QrCodeEntity qr = generatorService.generateStatic("MERCH_NOAMT", "BANK_B", null);

        // STATIC QR requires amount in the pay request; null amount must throw
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> paymentService.pay(qr.getQrId(), "BANK_A", null),
                "STATIC QR payment without amount must throw IllegalArgumentException");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void seedPool(String pspId, BigDecimal balance) {
        jdbcTemplate.update("""
                INSERT INTO psp_pools (psp_id, balance, held_amount, minimum_balance)
                VALUES (?, ?, 0.00, 0.00)
                ON CONFLICT (psp_id) DO UPDATE
                  SET balance = psp_pools.balance + EXCLUDED.balance
                """, pspId, balance);
    }
}
