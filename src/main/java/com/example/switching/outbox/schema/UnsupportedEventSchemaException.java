package com.example.switching.outbox.schema;

public class UnsupportedEventSchemaException extends RuntimeException {
    public UnsupportedEventSchemaException(String message) {
        super(message);
    }
}
