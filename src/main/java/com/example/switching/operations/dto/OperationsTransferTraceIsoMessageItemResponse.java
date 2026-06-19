package com.example.switching.operations.dto;

import java.time.LocalDateTime;

public record OperationsTransferTraceIsoMessageItemResponse(
        Long id,
        String correlationRef,
        String inquiryRef,
        String transferRef,
        String endToEndId,
        String messageId,
        String messageType,
        String direction,
        String securityStatus,
        String validationStatus,
        String errorCode,
        String errorMessage,
        LocalDateTime createdAt,
        String operationIsoMessageApiPath
) {
}
