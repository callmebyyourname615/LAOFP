package com.example.switching.billpayment.exception;

public class BillNotFoundException extends RuntimeException {
    public BillNotFoundException(String ref) {
        super("Bill not found: " + ref);
    }
}
