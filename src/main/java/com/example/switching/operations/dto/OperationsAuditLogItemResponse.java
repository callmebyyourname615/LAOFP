package com.example.switching.operations.dto;

import java.time.LocalDateTime;

public record OperationsAuditLogItemResponse(
        Long id,
        String eventType,
        String referenceType,
        String referenceId,
        String actor,
        String channelId,
        LocalDateTime createdAt,
        String payload
) {
}