package com.example.switching.operations.dto;

import java.time.LocalDateTime;

public record OperationsOutboxStuckItemResponse(
        Long id,
        String transferRef,
        String messageType,
        String status,
        Integer retryCount,
        String lastError,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime processedAt,
        LocalDateTime nextRetryAt,
        Long stuckMinutes,
        Boolean recoverable,
        String suggestedAction
) {
}