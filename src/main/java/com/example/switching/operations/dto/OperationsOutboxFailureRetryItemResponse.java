package com.example.switching.operations.dto;

public record OperationsOutboxFailureRetryItemResponse(
        Long outboxEventId,
        String transferRef,
        String messageType,
        String previousStatus,
        String newStatus,
        Integer retryCount,
        Boolean requeued,
        String message
) {
}