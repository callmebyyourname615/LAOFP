package com.example.switching.settlement.dto;

import java.time.LocalDateTime;

public record SettlementTimelineItemResponse(
        Long id,
        String eventType,
        String referenceType,
        String referenceId,
        String actor,
        String payload,
        String traceId,
        LocalDateTime createdAt
) {}
