package com.example.switching.settlement.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SettlementPositionResponse(
        Long id,
        String bankCode,
        String currency,
        BigDecimal debitAmount,
        BigDecimal creditAmount,
        BigDecimal netPosition,
        int transactionCount,
        String status,
        LocalDateTime settledAt
) {}
