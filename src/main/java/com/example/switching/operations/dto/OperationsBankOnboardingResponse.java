package com.example.switching.operations.dto;

import java.time.LocalDateTime;

public record OperationsBankOnboardingResponse(
        String status,
        LocalDateTime processedAt,

        String bankCode,
        String bankName,

        String connectorName,
        String connectorType,

        String sourceBank,
        String destinationBank,
        String messageType,
        String routeCode,

        Boolean participantCreated,
        Boolean participantAlreadyExisted,

        Boolean connectorCreated,
        Boolean connectorAlreadyExisted,

        Boolean routingRuleCreated,
        Boolean routingRuleAlreadyExisted,

        String nextStep,
        String message
) {
}