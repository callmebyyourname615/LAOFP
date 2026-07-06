package com.example.switching.dispute.exception;

public class DisputeReferenceNotFoundException extends RuntimeException {
    public DisputeReferenceNotFoundException(String transferRef) {
        super("Dispute reference transaction not found or not settled: " + transferRef);
    }
}
