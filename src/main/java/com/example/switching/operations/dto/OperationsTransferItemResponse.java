package com.example.switching.operations.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OperationsTransferItemResponse(
        Long id,
        String transferRef,
        String clientTransferId,
        String inquiryRef,
        String sourceBank,
        String debtorAccount,
        String destinationBank,
        String creditorAccount,
        String destinationAccountName,
        BigDecimal amount,
        String currency,
        String status,
        String currentStatus,
        String channelId,
        String routeCode,
        String connectorName,
        String externalReference,
        String reference,
        String confirmationStatus,
        String settlementConfidence,
        String errorCode,
        String errorMessage,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime settledAt,
        String transferApiPath,
        String traceApiPath,
        String inquiryApiPath
) {
}
