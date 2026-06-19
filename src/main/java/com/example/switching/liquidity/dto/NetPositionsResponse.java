package com.example.switching.liquidity.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Response for {@code GET /v1/settlement/positions}.
 *
 * <p>Returns the net multilateral positions for every participant in the
 * requested (or current OPEN) settlement cycle.
 */
public record NetPositionsResponse(
        String cycleRef,
        String cycleStatus,
        LocalDate settlementDate,
        List<PositionEntry> positions) {

    public record PositionEntry(
            String bankCode,
            String currency,
            BigDecimal debitAmount,
            BigDecimal creditAmount,
            BigDecimal netPosition,
            int transactionCount,
            String status) {
    }
}
