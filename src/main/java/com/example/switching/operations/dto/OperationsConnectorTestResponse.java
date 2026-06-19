package com.example.switching.operations.dto;

import java.time.LocalDateTime;

public record OperationsConnectorTestResponse(
        String status,
        LocalDateTime testedAt,
        String connectorName,
        String bankCode,
        String connectorType,
        Boolean enabled,
        Boolean forceReject,
        Boolean reachable,
        String responseCode,
        String responseMessage,
        Long responseTimeMs
) {
}