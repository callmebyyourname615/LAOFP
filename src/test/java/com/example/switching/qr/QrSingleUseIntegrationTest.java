package com.example.switching.qr;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.example.switching.AbstractIntegrationTest;
import com.example.switching.qr.entity.QrCodeEntity;
import com.example.switching.qr.exception.QrAlreadyUsedException;
import com.example.switching.qr.service.QrGeneratorService;
import com.example.switching.qr.service.QrPaymentService;

/**
 * Integration tests for DYNAMIC QR single-use enforcement (TC-QR-SU-001 – 002).
 *
 * Tests:
 * - Second pay on same DYNAMIC QR returns LFP-QR-002
 * - STATIC QR can be paid multiple times (not single-use)
 */
class QrSingleUseIntegrationTest extends AbstractIntegrationTest {

    @Autowired private QrGeneratorService generatorService;
    @Autowired private QrPaymentService   paymentService;
    @Autowired private JdbcTemplate       jdbcTemplate;

    // ── TC-QR-SU-001 ─────────────────────────────────────────────────────────

    @Test
    void pay_dynamicQr_secondAttempt_throwsQrAlreadyUsed() {
        seedPool("BANK_A", new BigDecimal("200000.00"));

        String txnRef = "TXN-SU-" + System.nanoTime();
        QrCodeEntity qr = generatorService.generateDynamic(
                "MERCH_SU", "BANK_B", new BigDecimal("1000.00"), txnRef, 600);

        // First pay succeeds
        paymentService.pay(qr.getQrId(), "BANK_A", null);

        // Second pay on the same DYNAMIC QR must throw LFP-QR-002
        assertThrows(QrAlreadyUsedException.class,
                () -> paymentService.pay(qr.getQrId(), "BANK_A", null),
                "Second DYNAMIC QR payment must throw QrAlreadyUsedException (LFP-QR-002)");
    }

    // ── TC-QR-SU-002 ─────────────────────────────────────────────────────────

    @Test
    void pay_staticQr_multiplePayments_allSucceed() {
        seedPool("BANK_A", new BigDecimal("500000.00"));

        QrCodeEntity qr = generatorService.generateStatic("MERCH_MULTI", "BANK_B", null);

        // STATIC QR can be used multiple times without error
        paymentService.pay(qr.getQrId(), "BANK_A", new BigDecimal("500.00"));
        paymentService.pay(qr.getQrId(), "BANK_A", new BigDecimal("750.00"));
        paymentService.pay(qr.getQrId(), "BANK_A", new BigDecimal("250.00"));
        // No exception expected
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
