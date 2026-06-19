package com.example.switching.dispute.exception;

public class DisputeWindowExpiredException extends RuntimeException {
    public DisputeWindowExpiredException(String txnRef) {
        super("Dispute window has expired (max 90 days) for transaction: " + txnRef);
    }
}
