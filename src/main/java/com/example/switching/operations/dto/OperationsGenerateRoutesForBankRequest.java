package com.example.switching.operations.dto;

public record OperationsGenerateRoutesForBankRequest(
        String bankCode,
        String messageType,
        String mode,
        Integer priority,
        Boolean enabled
) {
}