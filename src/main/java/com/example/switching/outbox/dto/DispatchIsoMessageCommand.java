package com.example.switching.outbox.dto;

public record DispatchIsoMessageCommand(
        String transferRef,
        Long isoMessageId,
        String messageId,
        String endToEndId,
        String messageType,
        String sourceBank,
        String destinationBank,
        String connectorName,
        String encryptedPayload
) {
}