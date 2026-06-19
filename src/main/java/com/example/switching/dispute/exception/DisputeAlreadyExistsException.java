package com.example.switching.dispute.exception;

public class DisputeAlreadyExistsException extends RuntimeException {
    public DisputeAlreadyExistsException(String txnRef) {
        super("An active dispute already exists for transaction: " + txnRef);
    }
}
