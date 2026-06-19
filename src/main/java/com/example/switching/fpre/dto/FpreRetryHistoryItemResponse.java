package com.example.switching.fpre.dto;

import java.time.LocalDateTime;

public record FpreRetryHistoryItemResponse(
        int attempt,
        LocalDateTime attemptedAt,
        String failureClass,
        String outboxStatus,
        String lastError,
        Integer httpStatus) {
}
