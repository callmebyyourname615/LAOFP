package com.example.switching.liquidity.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PoolBalance(
        String pspId,
        BigDecimal balance,
        BigDecimal heldAmount,
        BigDecimal availableBalance,
        String currency,
        BigDecimal minimumBalance,
        LocalDateTime lastUpdatedAt) {
}
