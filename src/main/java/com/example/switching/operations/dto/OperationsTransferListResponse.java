package com.example.switching.operations.dto;

import java.time.LocalDateTime;
import java.util.List;

public record OperationsTransferListResponse(
        String status,
        LocalDateTime checkedAt,
        Long totalItems,
        Integer returnedItems,
        Integer limit,
        Integer offset,
        List<OperationsTransferItemResponse> items
) {
}
