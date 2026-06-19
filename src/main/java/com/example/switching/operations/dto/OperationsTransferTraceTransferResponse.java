package com.example.switching.operations.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OperationsTransferTraceTransferResponse(
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
        String errorCode,
        String errorMessage,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String transferApiPath,
        String operationTransferApiPath,
        String operationTraceApiPath,
        String inquiryApiPath
) {
}
