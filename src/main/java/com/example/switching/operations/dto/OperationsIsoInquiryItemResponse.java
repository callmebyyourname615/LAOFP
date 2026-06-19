package com.example.switching.operations.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OperationsIsoInquiryItemResponse(
        Long id,
        String inquiryRef,
        String channelId,
        String messageId,
        String instructionId,
        String endToEndId,
        String sourceBank,
        String destinationBank,
        String debtorAccount,
        String creditorAccount,
        BigDecimal amount,
        String currency,
        String reference,
        String status,
        Boolean accountFound,
        Boolean bankAvailable,
        Boolean eligibleForTransfer,
        String failureCode,
        String failureMessage,
        LocalDateTime expiresAt,
        Boolean expired,
        String usedByTransferRef,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String inquiryApiPath,
        String transferApiPath
) {
}
