package com.example.switching.operations.dto;

import java.time.LocalDateTime;

public record OperationsBankStatusItemResponse(
        String bankCode,
        String bankName,
        String participantType,
        String participantStatus,
        String country,
        String currency,

        String healthStatus,
        String healthMessage,

        Long connectorTotal,
        Long connectorEnabled,
        Long connectorDisabled,
        Long connectorForceReject,

        Long outboundRouteTotal,
        Long outboundRouteEnabled,
        Long inboundRouteTotal,
        Long inboundRouteEnabled,

        Long outboundTransferTotal,
        Long outboundTransferSuccess,
        Long outboundTransferFailed,
        Long inboundTransferTotal,
        Long inboundTransferSuccess,
        Long inboundTransferFailed,

        Long relatedOutboxFailed,
        Long relatedOutboxPending,
        Long relatedOutboxProcessing,

        LocalDateTime lastOutboundTransferAt,
        LocalDateTime lastInboundTransferAt
) {
}