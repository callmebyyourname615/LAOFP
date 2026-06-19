package com.example.switching.operations.dto;

import java.time.LocalDateTime;
import java.util.List;

public record OperationsConnectorHealthListResponse(
        String status,
        LocalDateTime checkedAt,
        Integer totalConnectors,
        List<OperationsConnectorHealthItemResponse> items
) {
}