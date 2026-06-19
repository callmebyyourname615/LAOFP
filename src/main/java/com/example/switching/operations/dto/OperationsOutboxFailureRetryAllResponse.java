package com.example.switching.operations.dto;

import java.time.LocalDateTime;
import java.util.List;

public record OperationsOutboxFailureRetryAllResponse(
        String status,
        LocalDateTime processedAt,
        Integer requestedLimit,
        Long totalFailedBefore,
        Integer requeuedCount,
        List<OperationsOutboxFailureRetryItemResponse> items
) {
}