package com.example.switching.paymentorchestration;

import java.util.UUID;

public record PushPaymentResult(
        UUID executionId,
        String externalReference,
        PaymentExecutionStatus status,
        String message,
        Object channelResult) {

    public PushPaymentResult(
            UUID executionId,
            String externalReference,
            PaymentExecutionStatus status,
            String message) {
        this(executionId, externalReference, status, message, null);
    }
}
