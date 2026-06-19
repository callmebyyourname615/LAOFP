package com.example.switching.operations.dto;

import java.time.LocalDateTime;

public record OperationsIsoMessageItemResponse(
        Long id,
        String correlationRef,
        String inquiryRef,
        String transferRef,

        String sourceBank,
        String destinationBank,

        String messageId,
        String endToEndId,
        String messageType,
        String direction,
        String securityStatus,
        String validationStatus,

        String errorCode,
        String errorMessage,

        Boolean plainPayloadPresent,
        Boolean encryptedPayloadPresent,

        String plainPayload,
        String encryptedPayload,

        LocalDateTime createdAt,

        String traceApiPath,
        String validateApiPath
) {
}