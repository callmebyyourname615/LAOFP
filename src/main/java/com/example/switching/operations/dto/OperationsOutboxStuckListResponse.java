package com.example.switching.operations.dto;

import java.time.LocalDateTime;
import java.util.List;

public record OperationsOutboxStuckListResponse(
        String status,
        LocalDateTime checkedAt,
        Integer thresholdMinutes,
        Integer returnedItems,
        List<OperationsOutboxStuckItemResponse> items
) {
}