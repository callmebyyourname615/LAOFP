package com.example.switching.operations.dto;

import java.time.LocalDateTime;

public record OperationsOutboxMarkReviewedResponse(
        String status,
        LocalDateTime reviewedAt,
        Long outboxEventId,
        String transferRef,
        String previousStatus,
        String newStatus,
        Integer retryCount,
        String reason,
        String reviewedBy,
        String message
) {
}