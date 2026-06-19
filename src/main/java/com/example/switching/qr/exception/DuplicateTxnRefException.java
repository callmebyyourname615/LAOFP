package com.example.switching.qr.exception;

public class DuplicateTxnRefException extends RuntimeException {
    public DuplicateTxnRefException(String txnRef) {
        super("A QR code with transaction reference already exists: " + txnRef);
    }
}
