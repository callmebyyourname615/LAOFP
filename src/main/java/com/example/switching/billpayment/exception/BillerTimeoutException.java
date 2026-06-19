package com.example.switching.billpayment.exception;

public class BillerTimeoutException extends RuntimeException {
    public BillerTimeoutException(String billerCode) {
        super("Biller API did not respond within the timeout window: " + billerCode);
    }
}
