package com.example.switching.fpre.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record FpreTransferItemResponse(
        String txnId,
        String status,
        String sourceBank,
        String destinationBank,
        BigDecimal amount,
        String currency,
        String failureClass,
        Boolean willRetry,
        Integer attemptCount,
        LocalDateTime nextRetryAt,
        String errorCode,
        String errorMessage) {
}
