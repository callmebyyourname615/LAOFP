package com.example.switching.operations.dto;

import java.time.LocalDateTime;
import java.util.List;

public record OperationsIsoMessageListResponse(
        String status,
        LocalDateTime checkedAt,
        Long totalItems,
        Integer returnedItems,
        Integer limit,
        Integer offset,
        Boolean includePayload,
        List<OperationsIsoMessageItemResponse> items
) {
}