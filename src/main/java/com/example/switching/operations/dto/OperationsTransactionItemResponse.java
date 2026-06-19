package com.example.switching.operations.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OperationsTransactionItemResponse(
        Long id,
        String transferRef,
        String inquiryRef,
        String sourceBank,
        String destinationBank,
        String debtorAccount,
        String creditorAccount,
        BigDecimal amount,
        String currency,
        String status,
        String reference,
        String externalReference,
        String errorCode,
        String errorMessage,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String traceApiPath
) {
}