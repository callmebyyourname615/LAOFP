package com.example.switching.operations.dto;

import java.time.LocalDateTime;

public record OperationsTransferTraceTimelineItemResponse(
        LocalDateTime timestamp,
        String source,
        String eventType,
        String status,
        String messageType,
        String direction,
        String referenceId,
        String title,
        String description
) {
}
