package com.example.switching.operations.dto;

import java.time.LocalDateTime;
import java.util.List;

public record OperationsOutboxStuckRecoverAllResponse(
        String status,
        LocalDateTime processedAt,
        Integer thresholdMinutes,
        Integer requestedLimit,
        Integer recoveredCount,
        List<OperationsOutboxStuckRecoverItemResponse> items
) {
}