package com.example.switching.operations.dto;

import java.time.LocalDateTime;
import java.util.Map;

public record OperationsHealthResponse(
        String status,
        LocalDateTime checkedAt,
        Map<String, Object> database,
        Map<String, Long> transfers,
        Map<String, Long> outbox,
        Map<String, Long> participants,
        Map<String, Long> connectors,
        Map<String, Long> routingRules,
        String message
) {
}