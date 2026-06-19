package com.example.switching.crossborder;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.example.switching.AbstractIntegrationTest;
import com.example.switching.crossborder.adapter.PromptPayAdapter;
import com.example.switching.crossborder.dto.CrossBorderInitiateRequest;
import com.example.switching.crossborder.dto.FxQuoteRequest;
import com.example.switching.crossborder.dto.FxQuoteResponse;
import com.example.switching.crossborder.entity.FxQuoteEntity;
import com.example.switching.crossborder.exception.PurposeCodeRequiredException;
import com.example.switching.crossborder.repository.FxCorridorRepository;
import com.example.switching.crossborder.service.CrossBorderTransferService;
import com.example.switching.crossborder.service.FxQuoteService;

/**
 * Integration tests for purpose code enforcement (TC-CB-PURPOSE-001 – 002).
 */
class PurposeCodeRequiredIntegrationTest extends AbstractIntegrationTest {

    @MockitoBean PromptPayAdapter        promptPayAdapter;

    @Autowired CrossBorderTransferService transferService;
    @Autowired FxQuoteService             fxQuoteService;
    @Autowired FxCorridorRepository       corridorRepo;
    @Autowired JdbcTemplate               jdbcTemplate;

    // ── TC-CB-PURPOSE-001 — large transfer without purposeCode → LFP-CB-003 ─

    @Test
    void initiate_largAmountWithoutPurposeCode_throwsPurposeCodeRequired() {
        seedPool("BANK_A", new BigDecimal("20000000.00"));

        Long corridorId = corridorRepo
                .findBySourceCurrencyAndDestCurrencyAndStatus("LAK", "THB", "ACTIVE")
                .get(0).getCorridorId();

        when(promptPayAdapter.targetNetwork()).thenReturn("PROMPTPAY");
        when(promptPayAdapter.send(any(), any(FxQuoteEntity.class), anyLong()))
                .thenReturn("PP-MOCK-002");

        // 6M LAK > 5M threshold
        FxQuoteResponse quote = fxQuoteService.createQuote(new FxQuoteRequest(corridorId, new BigDecimal("6000000.00")));

        assertThrows(PurposeCodeRequiredException.class,
                () -> transferService.initiate(new CrossBorderInitiateRequest(
                        quote.quoteId(), "BANK_A",
                        "Big Merchant", "Bangkok Bank", "TH999", "TH",
                        null, null)),   // no purposeCode
                "Transfer >5M LAK without purposeCode must throw PurposeCodeRequiredException (LFP-CB-003)");
    }

    // ── TC-CB-PURPOSE-002 — large transfer WITH purposeCode succeeds ──────────

    @Test
    void initiate_largeAmountWithPurposeCode_succeeds() {
        seedPool("BANK_A", new BigDecimal("20000000.00"));

        Long corridorId = corridorRepo
                .findBySourceCurrencyAndDestCurrencyAndStatus("LAK", "THB", "ACTIVE")
                .get(0).getCorridorId();

        when(promptPayAdapter.targetNetwork()).thenReturn("PROMPTPAY");
        when(promptPayAdapter.send(any(), any(FxQuoteEntity.class), anyLong()))
                .thenReturn("PP-MOCK-003");

        FxQuoteResponse quote = fxQuoteService.createQuote(new FxQuoteRequest(corridorId, new BigDecimal("6000000.00")));

        assertDoesNotThrow(() -> transferService.initiate(new CrossBorderInitiateRequest(
                quote.quoteId(), "BANK_A",
                "Big Merchant", "Bangkok Bank", "TH999", "TH",
                "TRADE_PAYMENT", "EXPORT_PROCEEDS")),
                "Large transfer with purposeCode + sourceOfFunds must succeed");
    }

    private void seedPool(String pspId, BigDecimal balance) {
        jdbcTemplate.update("""
                INSERT INTO psp_pools (psp_id, balance, held_amount, minimum_balance)
                VALUES (?, ?, 0.00, 0.00)
                ON CONFLICT (psp_id) DO UPDATE SET balance = psp_pools.balance + EXCLUDED.balance
                """, pspId, balance);
    }
}
