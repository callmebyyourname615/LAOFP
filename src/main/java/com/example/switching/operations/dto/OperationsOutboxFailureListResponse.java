package com.example.switching.operations.dto;

import java.time.LocalDateTime;
import java.util.List;

public record OperationsOutboxFailureListResponse(
        String status,
        LocalDateTime checkedAt,
        Long totalFailed,
        Integer returnedItems,
        List<OperationsOutboxFailureItemResponse> items
) {
}