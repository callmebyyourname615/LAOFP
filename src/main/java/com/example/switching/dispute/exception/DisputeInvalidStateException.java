package com.example.switching.dispute.exception;

public class DisputeInvalidStateException extends RuntimeException {
    public DisputeInvalidStateException(Long disputeId, String action, String currentStatus, String requiredStatus) {
        super("Dispute " + disputeId + " cannot " + action
                + " from status " + currentStatus
                + "; required " + requiredStatus);
    }

    public DisputeInvalidStateException(Long disputeId, String message) {
        super("Dispute " + disputeId + " invalid state: " + message);
    }
}
