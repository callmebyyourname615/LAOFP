package com.example.switching.promotion.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PromotionBudgetReservation(
        UUID reservationId,
        long promotionId,
        String transactionRef,
        BigDecimal amount,
        String currency,
        String status,
        Instant expiresAt) {
}
