package com.example.switching.outbox.enums;

public enum FailureClass {
    TRANSIENT,
    PERMANENT_BUSINESS,
    PERMANENT_COMPLIANCE,
    AMBIGUOUS;

    public boolean shouldRetry() {
        return this == TRANSIENT || this == AMBIGUOUS;
    }

    public boolean shouldRejectTransfer() {
        return this == PERMANENT_BUSINESS || this == PERMANENT_COMPLIANCE;
    }
}
