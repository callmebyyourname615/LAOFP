package com.example.switching.rtp.exception;

public class RtpIdempotencyConflictException extends RuntimeException {
    public RtpIdempotencyConflictException(String correlationId) {
        super("requestCorrelationId was already used by this payee participant with a different payload: " + correlationId);
    }
}
