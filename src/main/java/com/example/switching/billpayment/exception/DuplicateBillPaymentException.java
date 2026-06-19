package com.example.switching.billpayment.exception;

public class DuplicateBillPaymentException extends RuntimeException {
    public DuplicateBillPaymentException(String billRef) {
        super("A confirmed payment already exists for bill reference within 24 hours: " + billRef);
    }
}
