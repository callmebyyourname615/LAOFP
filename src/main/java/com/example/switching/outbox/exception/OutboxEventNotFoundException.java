package com.example.switching.outbox.exception;

public class OutboxEventNotFoundException extends RuntimeException {

    public OutboxEventNotFoundException(String message) {
        super(message);
    }
}