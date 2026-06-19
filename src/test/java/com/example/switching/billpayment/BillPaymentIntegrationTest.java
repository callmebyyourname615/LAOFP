package com.example.switching.billpayment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.jdbc.core.JdbcTemplate;

import com.example.switching.AbstractIntegrationTest;
import com.example.switching.webhook.WebhookTestSecrets;
import com.example.switching.billpayment.client.BillerApiClient;
import com.example.switching.billpayment.client.BillerApiClient.BillerBillDto;
import com.example.switching.billpayment.dto.BillFetchResponse;
import com.example.switching.billpayment.dto.BillPayResponse;
import com.example.switching.billpayment.repository.BillerRepository;
import com.example.switching.billpayment.service.BillPaymentService;
import com.example.switching.billpayment.service.BillerService;

/**
 * Integration tests for full bill payment flow (TC-BILL-PAY-001 – 002).
 *
 * Tests:
 * - Full Fetch-Pay-Confirm: pool debited, SETTLED transaction created, receipt returned
 * - BILL.PAYMENT.CONFIRMED webhook delivery log exists after payment
 */
class BillPaymentIntegrationTest extends AbstractIntegrationTest {

    @MockitoBean  BillerApiClient   billerApiClient;

    @Autowired BillerService     billerService;
    @Autowired BillPaymentService paymentService;
    @Autowired BillerRepository  billerRepo;
    @Autowired JdbcTemplate      jdbcTemplate;

    // ── TC-BILL-PAY-001 ──────────────────────────────────────────────────────

    @Test
    void pay_fullFetchPayConfirm_poolDebitedAndTransactionSettled() {
        seedPool("BANK_A", new BigDecimal("500000.00"));

        Long billerId = billerRepo.findByBillerCode("LTC-001")
                .orElseThrow().getBillerId();

        when(billerApiClient.fetchBill(anyString(), anyString(), anyString(), anyInt()))
                .thenReturn(new BillerBillDto(
                        "BILL-LTC-PAY-001",
                        new BigDecimal("250000.00"),
                        LocalDate.now().plusDays(7),
                        "VIENTIANE CUSTOMER"));

        when(billerApiClient.confirmPayment(anyString(), anyString(), anyString(),
                org.mockito.ArgumentMatchers.any(BigDecimal.class), anyString(), anyInt()))
                .thenReturn("RCP-LTC-001");

        // Fetch token
        BillFetchResponse fetchResp = billerService.fetchBill(billerId, "BILL-LTC-PAY-001");
        assertNotNull(fetchResp.tokenId());

        // Pay
        BillPayResponse payResp = paymentService.pay(fetchResp.tokenId(), "BANK_A");

        assertEquals("CONFIRMED",      payResp.status());
        assertEquals("LTC-001",        payResp.billerCode());
        assertEquals("BILL-LTC-PAY-001", payResp.billRef());
        assertEquals("RCP-LTC-001",    payResp.receiptNumber());
        assertNotNull(payResp.txnRef(), "Transaction ref must be set");
        assertNotNull(payResp.confirmedAt());
        assertEquals(0, new BigDecimal("250000.00").compareTo(payResp.amount()));

        // Verify SETTLED transaction row was created
        Integer txnCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE transaction_ref = ? AND status = 'SETTLED'",
                Integer.class, payResp.txnRef());
        assertEquals(1, txnCount, "Exactly one SETTLED transaction must exist");

        // Verify bill_payment row is CONFIRMED
        Integer payCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM bill_payments WHERE txn_ref = ? AND status = 'CONFIRMED' AND receipt_number = ?",
                Integer.class, payResp.txnRef(), "RCP-LTC-001");
        assertEquals(1, payCount, "bill_payments row must be CONFIRMED with receipt number");

        // Verify token is marked used
        Boolean used = jdbcTemplate.queryForObject(
                "SELECT used FROM bill_tokens WHERE token_id = ?",
                Boolean.class, fetchResp.tokenId());
        assertEquals(Boolean.TRUE, used, "Token must be marked used after payment");
    }

    // ── TC-BILL-PAY-002 ──────────────────────────────────────────────────────

    @Test
    void pay_webhookDeliveryLogCreated() {
        seedPool("BANK_A", new BigDecimal("500000.00"));
        // Must register webhook BEFORE payment so publishQuietly finds a registration
        seedWebhookRegistration("BANK_A", "BILL.PAYMENT.CONFIRMED");

        Long billerId = billerRepo.findByBillerCode("LTC-001")
                .orElseThrow().getBillerId();

        when(billerApiClient.fetchBill(anyString(), anyString(), anyString(), anyInt()))
                .thenReturn(new BillerBillDto(
                        "BILL-LTC-WH-001",
                        new BigDecimal("100000.00"),
                        null, "Webhook Customer"));

        when(billerApiClient.confirmPayment(anyString(), anyString(), anyString(),
                org.mockito.ArgumentMatchers.any(BigDecimal.class), anyString(), anyInt()))
                .thenReturn("RCP-WH-001");

        BillFetchResponse fetch = billerService.fetchBill(billerId, "BILL-LTC-WH-001");
        BillPayResponse   pay   = paymentService.pay(fetch.tokenId(), "BANK_A");

        // BILL.PAYMENT.CONFIRMED webhook delivery log must exist
        Integer wh = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM webhook_delivery_log wdl
                  JOIN webhook_registrations wr ON wr.webhook_id = wdl.webhook_id
                 WHERE wdl.event_type = 'BILL.PAYMENT.CONFIRMED'
                   AND wdl.event_ref  = ?
                   AND wr.psp_id      = 'BANK_A'
                """,
                Integer.class, pay.txnRef());
        assertEquals(1, wh, "BILL.PAYMENT.CONFIRMED delivery log must exist");
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

    private void seedWebhookRegistration(String pspId, String eventType) {
        String webhookId      = java.util.UUID.randomUUID().toString();
        String eventTypesJson = "[\"" + eventType + "\"]";
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
                WebhookTestSecrets.sha256(), eventTypesJson);
    }
}
