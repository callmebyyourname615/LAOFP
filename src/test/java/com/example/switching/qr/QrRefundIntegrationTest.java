package com.example.switching.qr;

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
import com.example.switching.qr.dto.QrPayResponse;
import com.example.switching.qr.dto.QrRefundResponse;
import com.example.switching.qr.entity.QrCodeEntity;
import com.example.switching.qr.exception.QrRefundWindowExpiredException;
import com.example.switching.qr.service.QrGeneratorService;
import com.example.switching.qr.service.QrPaymentService;
import com.example.switching.qr.service.QrRefundService;
import com.example.switching.transfer.exception.TransferNotFoundException;

/**
 * Integration tests for QR refund flow (TC-QR-RFD-001 – 004).
 *
 * Tests:
 * - Refund within 30-day window succeeds
 * - Refund outside 30-day window throws LFP-QR-007
 * - Refund on unknown txnId throws TransferNotFoundException (404)
 * - Partial refund amount is accepted
 */
class QrRefundIntegrationTest extends AbstractIntegrationTest {

    @Autowired private QrGeneratorService generatorService;
    @Autowired private QrPaymentService   paymentService;
    @Autowired private QrRefundService    refundService;
    @Autowired private JdbcTemplate       jdbcTemplate;

    // ── TC-QR-RFD-001 ────────────────────────────────────────────────────────

    @Test
    void refund_withinWindow_succeeds() {
        seedPool("BANK_A", new BigDecimal("100000.00"));

        String txnRef = "TXN-RFD-" + System.nanoTime();
        QrCodeEntity qr = generatorService.generateDynamic(
                "MERCH_RFD", "BANK_B", new BigDecimal("3000.00"), txnRef, 300);
        QrPayResponse paid = paymentService.pay(qr.getQrId(), "BANK_A", null);

        // Refund within window
        QrRefundResponse refund = refundService.refund(paid.txnId(), new BigDecimal("3000.00"));

        assertEquals("COMPLETED", refund.status());
        assertNotNull(refund.refundTxnId(), "Refund txnId must be returned");
        assertEquals(paid.txnId(), refund.originalTxnId());
        assertEquals(0, new BigDecimal("3000.00").compareTo(refund.amount()));
        assertNotNull(refund.initiatedAt());

        // Verify SETTLED refund transaction in DB
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE transaction_ref = ? AND status = 'SETTLED'",
                Integer.class, refund.refundTxnId());
        assertEquals(1, count, "Refund transaction must be persisted as SETTLED");
    }

    // ── TC-QR-RFD-002 ────────────────────────────────────────────────────────

    @Test
    void refund_outsideWindow_throwsQrRefundWindowExpired() {
        // Seed a QR transaction created 31 days ago by manipulating created_at
        String txnRef = "TXN-OLD-" + System.nanoTime();
        LocalDate oldDate = LocalDate.now().minusDays(31);
        LocalDateTime oldTime = oldDate.atTime(10, 0);

        // Use today as business_date (partition range is CURRENT_DATE ±7/+90 days),
        // but set created_at to 31 days ago so the refund window check fails.
        jdbcTemplate.update("""
                INSERT INTO transactions (
                    transaction_ref, idempotency_key, flow_ref,
                    source_bank, source_account_no,
                    destination_bank, destination_account_no, destination_account_name,
                    amount, currency, channel_id, route_code, connector_name,
                    status, external_reference, reference,
                    settlement_method, high_value,
                    business_date, accepted_at, settled_at, created_at
                ) VALUES (?, ?, ?, 'BANK_A', 'QR_OLD', 'BANK_B', 'QR_OLD_DST', 'Old Merchant',
                    1000.00, 'LAK', 'QR', 'ROUTE_QR', 'QR_SERVICE',
                    'SETTLED', ?, ?,
                    'DNS', false,
                    ?, ?, ?, ?)
                """,
                txnRef, txnRef, "QR-OLD",
                txnRef, txnRef,
                LocalDate.now(), oldTime, oldTime, oldTime);

        assertThrows(QrRefundWindowExpiredException.class,
                () -> refundService.refund(txnRef, new BigDecimal("1000.00")),
                "Refund after 30-day window must throw QrRefundWindowExpiredException");
    }

    // ── TC-QR-RFD-003 ────────────────────────────────────────────────────────

    @Test
    void refund_unknownTxnId_throwsTransferNotFound() {
        assertThrows(TransferNotFoundException.class,
                () -> refundService.refund("NONEXISTENT-TXN-" + System.nanoTime(),
                        new BigDecimal("100.00")),
                "Unknown txnId must throw TransferNotFoundException");
    }

    // ── TC-QR-RFD-004 ────────────────────────────────────────────────────────

    @Test
    void refund_partialAmount_succeeds() {
        seedPool("BANK_A", new BigDecimal("100000.00"));

        String txnRef = "TXN-PART-" + System.nanoTime();
        QrCodeEntity qr = generatorService.generateDynamic(
                "MERCH_PART", "BANK_B", new BigDecimal("10000.00"), txnRef, 300);
        QrPayResponse paid = paymentService.pay(qr.getQrId(), "BANK_A", null);

        // Partial refund (50%)
        QrRefundResponse refund = refundService.refund(paid.txnId(), new BigDecimal("5000.00"));

        assertEquals("COMPLETED", refund.status());
        assertEquals(0, new BigDecimal("5000.00").compareTo(refund.amount()),
                "Partial refund amount must match requested amount");
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
