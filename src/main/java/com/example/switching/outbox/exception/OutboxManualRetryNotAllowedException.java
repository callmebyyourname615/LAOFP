package com.example.switching.outbox.exception;

public class OutboxManualRetryNotAllowedException extends RuntimeException {

    public OutboxManualRetryNotAllowedException(String message) {
        super(message);
    }
}