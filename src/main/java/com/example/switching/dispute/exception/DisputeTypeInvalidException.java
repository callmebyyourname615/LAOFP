package com.example.switching.dispute.exception;

public class DisputeTypeInvalidException extends RuntimeException {
    public DisputeTypeInvalidException(String type) {
        super("Invalid dispute type: " + type);
    }
}
