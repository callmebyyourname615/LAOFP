package com.example.switching.inquiry.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record InquiryMonitorItemResponse(
        Long id,
        String inquiryRef,
        String clientInquiryId,
        String sourceBank,
        String destinationBank,
        String creditorAccount,
        BigDecimal amount,
        String currency,
        String status,
        Boolean accountFound,
        Boolean bankAvailable,
        Boolean eligibleForTransfer,
        String destinationAccountName,
        String message,
        String reference,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}