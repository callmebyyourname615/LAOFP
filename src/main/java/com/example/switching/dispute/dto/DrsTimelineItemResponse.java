package com.example.switching.dispute.dto;

import java.time.LocalDateTime;

public record DrsTimelineItemResponse(
        Long id,
        String eventType,
        String referenceType,
        String referenceId,
        String actor,
        String payload,
        String traceId,
        LocalDateTime createdAt
) {}
