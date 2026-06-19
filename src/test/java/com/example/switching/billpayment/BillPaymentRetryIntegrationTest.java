package com.example.switching.billpayment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.jdbc.core.JdbcTemplate;

import com.example.switching.AbstractIntegrationTest;
import com.example.switching.billpayment.client.BillerApiClient;
import com.example.switching.billpayment.client.BillerApiClient.BillerBillDto;
import com.example.switching.billpayment.dto.BillFetchResponse;
import com.example.switching.billpayment.dto.BillPayResponse;
import com.example.switching.billpayment.exception.BillerTimeoutException;
import com.example.switching.billpayment.exception.BillTokenExpiredException;
import com.example.switching.billpayment.repository.BillerRepository;
import com.example.switching.billpayment.service.BillPaymentService;
import com.example.switching.billpayment.service.BillerService;

/**
 * Integration tests for biller timeout retry within token TTL (TC-BILL-RETRY-001 – 002).
 *
 * Tests:
 * - Biller fails first call → BillerTimeoutException (504); retry with same token succeeds
 * - Token expired before retry → BillTokenExpiredException (LFP-6002)
 */
class BillPaymentRetryIntegrationTest extends AbstractIntegrationTest {

    @MockitoBean  BillerApiClient   billerApiClient;

    @Autowired BillerService     billerService;
    @Autowired BillPaymentService paymentService;
    @Autowired BillerRepository  billerRepo;
    @Autowired JdbcTemplate      jdbcTemplate;

    // ── TC-BILL-RETRY-001 ────────────────────────────────────────────────────

    @Test
    void pay_billerFailsFirstThenSucceeds_retryWithSameToken() {
        seedPool("BANK_A", new BigDecimal("500000.00"));

        Long billerId = billerRepo.findByBillerCode("EDL-001")
                .orElseThrow().getBillerId();
        String billRef = "BILL-RETRY-" + System.nanoTime();

        when(billerApiClient.fetchBill(anyString(), anyString(), anyString(), anyInt()))
                .thenReturn(new BillerBillDto(
                        billRef, new BigDecimal("75000.00"), null, "Retry Customer"));

        // First confirmPayment call throws; second call succeeds
        when(billerApiClient.confirmPayment(anyString(), anyString(), anyString(),
                any(BigDecimal.class), anyString(), anyInt()))
                .thenThrow(new BillerTimeoutException("EDL-001"))
                .thenReturn("RCP-RETRY-OK");

        BillFetchResponse fetch = billerService.fetchBill(billerId, billRef);

        // First attempt → BillerTimeoutException (triggers full transaction rollback)
        assertThrows(BillerTimeoutException.class,
                () -> paymentService.pay(fetch.tokenId(), "BANK_A"),
                "First attempt: biller timeout must throw BillerTimeoutException (LFP-6004)");

        // Token is still valid (not marked used due to rollback) — retry succeeds
        BillPayResponse retry = paymentService.pay(fetch.tokenId(), "BANK_A");

        assertEquals("CONFIRMED",   retry.status());
        assertEquals("RCP-RETRY-OK", retry.receiptNumber());
        assertEquals(0, new BigDecimal("75000.00").compareTo(retry.amount()));
    }

    // ── TC-BILL-RETRY-002 ────────────────────────────────────────────────────

    @Test
    void pay_tokenExpiredBeforeRetry_throwsBillTokenExpired() {
        seedPool("BANK_A", new BigDecimal("500000.00"));

        Long billerId = billerRepo.findByBillerCode("EDL-001")
                .orElseThrow().getBillerId();
        String billRef = "BILL-EXPRETRY-" + System.nanoTime();

        when(billerApiClient.fetchBill(anyString(), anyString(), anyString(), anyInt()))
                .thenReturn(new BillerBillDto(
                        billRef, new BigDecimal("20000.00"), null, "Expired Retry Customer"));

        when(billerApiClient.confirmPayment(anyString(), anyString(), anyString(),
                any(BigDecimal.class), anyString(), anyInt()))
                .thenThrow(new BillerTimeoutException("EDL-001"));

        BillFetchResponse fetch = billerService.fetchBill(billerId, billRef);

        // First attempt fails
        assertThrows(BillerTimeoutException.class,
                () -> paymentService.pay(fetch.tokenId(), "BANK_A"));

        // Force-expire the token (simulate token TTL elapsed between retry attempts)
        jdbcTemplate.update(
                "UPDATE bill_tokens SET expires_at = NOW() - INTERVAL '1 minute' WHERE token_id = ?",
                fetch.tokenId());

        // Retry after expiry → must throw BillTokenExpiredException (LFP-6002)
        // PSP must re-fetch the bill to get a new token
        assertThrows(BillTokenExpiredException.class,
                () -> paymentService.pay(fetch.tokenId(), "BANK_A"),
                "Retry after token expiry must throw BillTokenExpiredException (LFP-6002)");
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
