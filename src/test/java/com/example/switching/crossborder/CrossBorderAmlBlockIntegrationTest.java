package com.example.switching.crossborder;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.example.switching.AbstractIntegrationTest;
import com.example.switching.aml.exception.SanctionsBlockException;
import com.example.switching.aml.service.SanctionsListSyncService;
import com.example.switching.crossborder.adapter.PromptPayAdapter;
import com.example.switching.crossborder.dto.CrossBorderInitiateRequest;
import com.example.switching.crossborder.dto.FxQuoteRequest;
import com.example.switching.crossborder.dto.FxQuoteResponse;
import com.example.switching.crossborder.repository.FxCorridorRepository;
import com.example.switching.crossborder.service.CrossBorderTransferService;
import com.example.switching.crossborder.service.FxQuoteService;

/**
 * Integration tests for AML blocking in cross-border payment (TC-CB-AML-001).
 *
 * LFP-CB-004 reuses SanctionsBlockException from P19.
 */
class CrossBorderAmlBlockIntegrationTest extends AbstractIntegrationTest {

    @MockitoBean PromptPayAdapter        promptPayAdapter;

    @Autowired CrossBorderTransferService transferService;
    @Autowired FxQuoteService             fxQuoteService;
    @Autowired FxCorridorRepository       corridorRepo;
    @Autowired JdbcTemplate               jdbcTemplate;
    @Autowired SanctionsListSyncService   sanctionsListSyncService;

    // ── TC-CB-AML-001 ────────────────────────────────────────────────────────

    @Test
    void initiate_sanctionedBeneficiary_throwsSanctionsBlockException() {
        seedPool("BANK_A", new BigDecimal("5000000.00"));
        seedSanctionsEntry("OFAC_BLOCKED_ENTITY_CB");

        Long corridorId = corridorRepo
                .findBySourceCurrencyAndDestCurrencyAndStatus("LAK", "THB", "ACTIVE")
                .get(0).getCorridorId();

        FxQuoteResponse quote = fxQuoteService.createQuote(
                new FxQuoteRequest(corridorId, new BigDecimal("500000.00")));

        // Beneficiary name matches sanctioned entity — must be blocked before adapter call
        assertThrows(SanctionsBlockException.class,
                () -> transferService.initiate(new CrossBorderInitiateRequest(
                        quote.quoteId(), "BANK_A",
                        "OFAC_BLOCKED_ENTITY_CB", "Unknown Bank", "XX9999", "XX",
                        null, null)),
                "Sanctioned beneficiary must throw SanctionsBlockException (LFP-CB-004)");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void seedPool(String pspId, BigDecimal balance) {
        jdbcTemplate.update("""
                INSERT INTO psp_pools (psp_id, balance, held_amount, minimum_balance)
                VALUES (?, ?, 0.00, 0.00)
                ON CONFLICT (psp_id) DO UPDATE SET balance = psp_pools.balance + EXCLUDED.balance
                """, pspId, balance);
    }

    private void seedSanctionsEntry(String name) {
        sanctionsListSyncService.seedTestEntry(name, "OFAC", "ENTITY", "TC-CB-AML-001");
    }
}
