package com.example.switching.billpayment;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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
import com.example.switching.billpayment.client.BillerApiClient;
import com.example.switching.billpayment.client.BillerApiClient.BillerBillDto;
import com.example.switching.billpayment.dto.BillFetchResponse;
import com.example.switching.billpayment.exception.DuplicateBillPaymentException;
import com.example.switching.billpayment.repository.BillerRepository;
import com.example.switching.billpayment.service.BillPaymentService;
import com.example.switching.billpayment.service.BillerService;

/**
 * Integration tests for duplicate bill payment enforcement (TC-BILL-DUP-001 – 002).
 *
 * Tests:
 * - Same billRef on the same biller twice within 24h → LFP-6003 on second attempt
 * - Different billRef on the same biller within 24h is allowed
 */
class BillDuplicatePaymentIntegrationTest extends AbstractIntegrationTest {

    @MockitoBean  BillerApiClient   billerApiClient;

    @Autowired BillerService     billerService;
    @Autowired BillPaymentService paymentService;
    @Autowired BillerRepository  billerRepo;
    @Autowired JdbcTemplate      jdbcTemplate;

    // ── TC-BILL-DUP-001 ──────────────────────────────────────────────────────

    @Test
    void pay_sameBillRefTwiceWithin24h_throwsDuplicateBillPayment() {
        seedPool("BANK_A", new BigDecimal("1000000.00"));

        Long billerId = billerRepo.findByBillerCode("EDL-001")
                .orElseThrow().getBillerId();

        String billRef = "BILL-DUP-" + System.nanoTime();

        when(billerApiClient.fetchBill(anyString(), anyString(), anyString(), anyInt()))
                .thenReturn(new BillerBillDto(
                        billRef, new BigDecimal("50000.00"),
                        LocalDate.now().plusDays(5), "Dup Customer"));

        when(billerApiClient.confirmPayment(anyString(), anyString(), anyString(),
                any(BigDecimal.class), anyString(), anyInt()))
                .thenReturn("RCP-DUP-001");

        // First payment succeeds
        BillFetchResponse fetch1 = billerService.fetchBill(billerId, billRef);
        paymentService.pay(fetch1.tokenId(), "BANK_A");

        // Second fetch + pay on same billRef within 24h must throw LFP-6003
        when(billerApiClient.fetchBill(anyString(), anyString(), anyString(), anyInt()))
                .thenReturn(new BillerBillDto(
                        billRef, new BigDecimal("50000.00"),
                        LocalDate.now().plusDays(5), "Dup Customer"));

        BillFetchResponse fetch2 = billerService.fetchBill(billerId, billRef);

        assertThrows(DuplicateBillPaymentException.class,
                () -> paymentService.pay(fetch2.tokenId(), "BANK_A"),
                "Second payment for same billRef within 24h must throw DuplicateBillPaymentException (LFP-6003)");
    }

    // ── TC-BILL-DUP-002 ──────────────────────────────────────────────────────

    @Test
    void pay_differentBillRefSameBiller_bothSucceed() {
        seedPool("BANK_A", new BigDecimal("1000000.00"));

        Long billerId = billerRepo.findByBillerCode("EDL-001")
                .orElseThrow().getBillerId();

        String ref1 = "BILL-DIFF-A-" + System.nanoTime();
        String ref2 = "BILL-DIFF-B-" + System.nanoTime();

        when(billerApiClient.fetchBill(anyString(), anyString(), anyString(), anyInt()))
                .thenReturn(new BillerBillDto(
                        ref1, new BigDecimal("30000.00"), null, "Customer A"))
                .thenReturn(new BillerBillDto(
                        ref2, new BigDecimal("45000.00"), null, "Customer B"));

        when(billerApiClient.confirmPayment(anyString(), anyString(), anyString(),
                any(BigDecimal.class), anyString(), anyInt()))
                .thenReturn("RCP-DIFF-A")
                .thenReturn("RCP-DIFF-B");

        // Both payments must succeed — no duplicate
        BillFetchResponse f1 = billerService.fetchBill(billerId, ref1);
        paymentService.pay(f1.tokenId(), "BANK_A");

        BillFetchResponse f2 = billerService.fetchBill(billerId, ref2);
        paymentService.pay(f2.tokenId(), "BANK_A"); // must not throw
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
