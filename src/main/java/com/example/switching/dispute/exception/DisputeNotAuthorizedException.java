package com.example.switching.dispute.exception;

public class DisputeNotAuthorizedException extends RuntimeException {
    public DisputeNotAuthorizedException(Long disputeId) {
        super("PSP is not authorized to act on dispute: " + disputeId);
    }
}
