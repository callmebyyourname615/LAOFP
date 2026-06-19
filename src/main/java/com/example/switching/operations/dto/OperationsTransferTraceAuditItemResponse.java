package com.example.switching.operations.dto;

import java.time.LocalDateTime;

public record OperationsTransferTraceAuditItemResponse(
        Long id,
        String eventType,
        String referenceType,
        String referenceId,
        String actor,
        String payload,
        LocalDateTime createdAt
) {
}
