package com.example.switching.crossborder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import com.example.switching.crossborder.dto.CrossBorderInitiateResponse;
import com.example.switching.crossborder.dto.FxQuoteRequest;
import com.example.switching.crossborder.dto.FxQuoteResponse;
import com.example.switching.crossborder.entity.FxQuoteEntity;
import com.example.switching.crossborder.repository.FxCorridorRepository;
import com.example.switching.crossborder.service.CrossBorderTransferService;
import com.example.switching.crossborder.service.FxQuoteService;

/**
 * Integration tests for cross-border initiation happy path (TC-CB-INIT-001 – 002).
 */
class CrossBorderInitiateIntegrationTest extends AbstractIntegrationTest {

    @MockitoBean PromptPayAdapter        promptPayAdapter;

    @Autowired CrossBorderTransferService transferService;
    @Autowired FxQuoteService             fxQuoteService;
    @Autowired FxCorridorRepository       corridorRepo;
    @Autowired JdbcTemplate               jdbcTemplate;

    // ── TC-CB-INIT-001 ───────────────────────────────────────────────────────

    @Test
    void initiate_happyPath_settlementTransactionCreated() {
        seedPool("BANK_A", new BigDecimal("5000000.00"));

        Long corridorId = corridorRepo
                .findBySourceCurrencyAndDestCurrencyAndStatus("LAK", "THB", "ACTIVE")
                .get(0).getCorridorId();

        // Mock PromptPay adapter — returns a network txn ID
        when(promptPayAdapter.targetNetwork()).thenReturn("PROMPTPAY");
        when(promptPayAdapter.send(any(), any(FxQuoteEntity.class), anyLong()))
                .thenReturn("PP-MOCK-TXN-001");

        FxQuoteResponse quote = fxQuoteService.createQuote(new FxQuoteRequest(corridorId, new BigDecimal("1000000.00")));

        CrossBorderInitiateRequest req = new CrossBorderInitiateRequest(
                quote.quoteId(), "BANK_A",
                "Thai Recipient Co", "Bangkok Bank", "TH123456789", "TH",
                null, null);

        CrossBorderInitiateResponse resp = transferService.initiate(req);

        assertEquals("COMPLETED",      resp.status());
        assertEquals("PROMPTPAY",      resp.targetNetwork());
        assertEquals("PP-MOCK-TXN-001", resp.networkTxnId());
        assertNotNull(resp.txnRef(),    "Transaction ref must be set");
        assertNotNull(resp.cbId(),      "CB ID must be set");
        assertEquals(0, new BigDecimal("1000000.00").compareTo(resp.sourceAmount()));

        // Verify SETTLED transaction persisted
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE transaction_ref = ? AND status = 'SETTLED'",
                Integer.class, resp.txnRef());
        assertEquals(1, count, "SETTLED transaction must exist");

        // Verify crossborder_transfers COMPLETED
        Integer cbCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM crossborder_transfers WHERE cb_id = ? AND status = 'COMPLETED' AND network_txn_id = 'PP-MOCK-TXN-001'",
                Integer.class, resp.cbId());
        assertEquals(1, cbCount, "crossborder_transfers must be COMPLETED");

        // Verify quote marked used
        Boolean used = jdbcTemplate.queryForObject(
                "SELECT used FROM fx_quotes WHERE quote_id = ?", Boolean.class, quote.quoteId());
        assertEquals(Boolean.TRUE, used, "FX quote must be marked used after payment");
    }

    // ── TC-CB-INIT-002 — suspended corridor ──────────────────────────────────

    @Test
    void initiate_suspendedCorridor_throwsCorridorNotAvailable() {
        // The SWIFT LAK→THB corridor is seeded as SUSPENDED
        Long suspendedId = corridorRepo.findByStatus("SUSPENDED").stream()
                .findFirst().orElseThrow().getCorridorId();

        assertThrows(com.example.switching.crossborder.exception.CorridorNotAvailableException.class,
                () -> fxQuoteService.createQuote(new FxQuoteRequest(suspendedId, new BigDecimal("5000000.00"))),
                "Suspended corridor must throw CorridorNotAvailableException (LFP-CB-002)");
    }

    private void seedPool(String pspId, BigDecimal balance) {
        jdbcTemplate.update("""
                INSERT INTO psp_pools (psp_id, balance, held_amount, minimum_balance)
                VALUES (?, ?, 0.00, 0.00)
                ON CONFLICT (psp_id) DO UPDATE SET balance = psp_pools.balance + EXCLUDED.balance
                """, pspId, balance);
    }

    private void assertThrows(Class<? extends Exception> ex, org.junit.jupiter.api.function.Executable f, String msg) {
        org.junit.jupiter.api.Assertions.assertThrows(ex, f, msg);
    }
}
