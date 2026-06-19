package com.example.switching.dispute.exception;

public class DisputeNotFoundException extends RuntimeException {
    public DisputeNotFoundException(Long disputeId) {
        super("Dispute not found: " + disputeId);
    }
}
