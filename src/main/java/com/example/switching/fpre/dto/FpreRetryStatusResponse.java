package com.example.switching.fpre.dto;

import java.time.LocalDateTime;

public record FpreRetryStatusResponse(
        String txnId,
        String transferStatus,
        String outboxStatus,
        int attemptCount,
        int maxAttempts,
        LocalDateTime nextRetryAt,
        String failureClass,
        boolean willAutoReverse,
        boolean willRetry,
        String errorCode,
        String errorMessage) {
}
