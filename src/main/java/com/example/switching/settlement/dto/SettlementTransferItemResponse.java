package com.example.switching.settlement.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SettlementTransferItemResponse(
        String transferRef,
        String clientTransferId,
        String sourceBank,
        String destinationBank,
        BigDecimal amount,
        String currency,
        String status,
        String confirmationStatus,
        String settlementConfidence,
        String externalReference,
        LocalDateTime createdAt,
        LocalDateTime settledAt
) {}
