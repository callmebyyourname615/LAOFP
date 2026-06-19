package com.example.switching.crossborder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.example.switching.AbstractIntegrationTest;
import com.example.switching.crossborder.dto.FxQuoteRequest;
import com.example.switching.crossborder.dto.FxQuoteResponse;
import com.example.switching.crossborder.dto.FxRateResponse;
import com.example.switching.crossborder.exception.CorridorNotAvailableException;
import com.example.switching.crossborder.exception.FxQuoteExpiredException;
import com.example.switching.crossborder.repository.FxCorridorRepository;
import com.example.switching.crossborder.service.FxQuoteService;

import java.util.List;

/**
 * Integration tests for FX quote flow (TC-CB-QUOTE-001 – 003).
 */
class FxQuoteIntegrationTest extends AbstractIntegrationTest {

    @Autowired FxQuoteService       fxQuoteService;
    @Autowired FxCorridorRepository corridorRepo;
    @Autowired JdbcTemplate         jdbcTemplate;

    // ── TC-CB-QUOTE-001 ──────────────────────────────────────────────────────

    @Test
    void createQuote_lakhToThb_returnsCorrectRateFeeAndExpiry() {
        Long corridorId = corridorRepo
                .findBySourceCurrencyAndDestCurrencyAndStatus("LAK", "THB", "ACTIVE")
                .get(0).getCorridorId();

        FxQuoteResponse quote = fxQuoteService.createQuote(new FxQuoteRequest(corridorId, new BigDecimal("1000000.00")));

        assertNotNull(quote.quoteId());
        assertEquals("LAK",       quote.sourceCurrency());
        assertEquals("THB",       quote.destCurrency());
        assertEquals("PROMPTPAY", quote.targetNetwork());
        assertTrue(quote.rate().compareTo(BigDecimal.ZERO) > 0,   "Rate must be positive");
        assertTrue(quote.fee().compareTo(BigDecimal.ZERO) > 0,    "Fee must be positive");
        assertTrue(quote.destAmount().compareTo(BigDecimal.ZERO) > 0, "Dest amount must be positive");
        assertNotNull(quote.expiresAt());
        assertTrue(quote.expiresAt().isAfter(LocalDateTime.now()), "Quote must not be immediately expired");
        assertTrue(quote.expiresAt().isBefore(LocalDateTime.now().plusSeconds(35)), "Quote TTL must be ≤30s");
    }

    // ── TC-CB-QUOTE-002 ──────────────────────────────────────────────────────

    @Test
    void requireValidQuote_expiredQuote_throwsFxQuoteExpired() {
        Long corridorId = corridorRepo
                .findBySourceCurrencyAndDestCurrencyAndStatus("LAK", "THB", "ACTIVE")
                .get(0).getCorridorId();

        FxQuoteResponse quote = fxQuoteService.createQuote(new FxQuoteRequest(corridorId, new BigDecimal("500000.00")));

        // Force-expire the quote
        jdbcTemplate.update("UPDATE fx_quotes SET expires_at = NOW() - INTERVAL '1 minute' WHERE quote_id = ?", quote.quoteId());

        assertThrows(FxQuoteExpiredException.class,
                () -> fxQuoteService.requireValidQuote(quote.quoteId()),
                "Expired quote must throw FxQuoteExpiredException (LFP-CB-001)");
    }

    // ── TC-CB-QUOTE-003 ──────────────────────────────────────────────────────

    @Test
    void getIndicativeRates_lakToThb_returnsActiveCorridors() {
        List<FxRateResponse> rates = fxQuoteService.getIndicativeRates("LAK", "THB");

        assertNotNull(rates);
        assertTrue(rates.size() >= 1, "At least one LAK→THB corridor must exist");
        rates.forEach(r -> {
            assertEquals("LAK",  r.sourceCurrency());
            assertEquals("THB",  r.destCurrency());
            assertEquals("ACTIVE",
                    corridorRepo.findById(r.corridorId()).orElseThrow().getStatus());
        });
    }

    // ── TC-CB-QUOTE-004 — below minimum ──────────────────────────────────────

    @Test
    void createQuote_belowMinAmount_throwsCorridorNotAvailable() {
        Long corridorId = corridorRepo
                .findBySourceCurrencyAndDestCurrencyAndStatus("LAK", "THB", "ACTIVE")
                .get(0).getCorridorId();

        assertThrows(CorridorNotAvailableException.class,
                () -> fxQuoteService.createQuote(new FxQuoteRequest(corridorId, new BigDecimal("100.00"))),
                "Amount below minimum must throw CorridorNotAvailableException (LFP-CB-002)");
    }
}
