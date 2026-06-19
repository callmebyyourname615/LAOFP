package com.example.switching.operations.dto;

import java.time.LocalDateTime;

public record OperationsConnectorHealthItemResponse(
        Long id,
        String connectorName,
        String bankCode,
        String bankName,
        String participantStatus,
        String connectorType,
        String endpointUrl,
        Integer timeoutMs,
        Boolean enabled,
        Boolean forceReject,
        String rejectReasonCode,
        String rejectReasonMessage,

        String healthStatus,
        String healthMessage,

        Long inboundRouteTotal,
        Long inboundRouteEnabled,
        Long outboundRouteTotal,
        Long outboundRouteEnabled,

        Long relatedTransferTotal,
        Long relatedTransferSuccess,
        Long relatedTransferFailed,

        Long relatedOutboxFailed,
        Long relatedOutboxProcessing,
        Long relatedOutboxPending,

        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String testApiPath
) {
}