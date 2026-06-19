package com.example.switching.operations.dto;

public record OperationsOutboxStuckRecoverItemResponse(
        Long outboxEventId,
        String transferRef,
        String messageType,
        String previousStatus,
        String newStatus,
        Integer retryCount,
        Long stuckMinutes,
        Boolean recovered,
        String message
) {
}