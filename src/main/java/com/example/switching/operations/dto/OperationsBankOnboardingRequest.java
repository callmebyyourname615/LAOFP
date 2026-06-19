package com.example.switching.operations.dto;

public record OperationsBankOnboardingRequest(
        String bankCode,
        String bankName,
        String participantType,
        String participantStatus,
        String country,
        String currency,

        String connectorName,
        String connectorType,
        String endpointUrl,
        Integer timeoutMs,
        Boolean connectorEnabled,
        Boolean forceReject,
        String rejectReasonCode,
        String rejectReasonMessage,

        String sourceBank,
        String messageType,
        String routeCode,
        Integer priority,
        Boolean routeEnabled
) {
}