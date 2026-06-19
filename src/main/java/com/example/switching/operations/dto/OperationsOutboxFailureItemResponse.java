package com.example.switching.operations.dto;

import java.time.LocalDateTime;

public record OperationsOutboxFailureItemResponse(
        Long id,
        String transferRef,
        String messageType,
        String status,
        Integer retryCount,
        String lastError,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime nextRetryAt,
        Boolean retryable,
        String retryApiPath
) {
}