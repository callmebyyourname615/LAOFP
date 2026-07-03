package com.example.switching.dispute.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record DrsTransferSnapshotResponse(
        String transferRef,
        String sourceBank,
        String destinationBank,
        BigDecimal amount,
        String currency,
        String status,
        String currentStatus,
        String confirmationStatus,
        String settlementConfidence,
        String externalReference,
        String reference,
        LocalDateTime settledAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
