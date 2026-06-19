package com.example.switching.aml.exception;

/**
 * Thrown when the sanctions screening query exceeds the configured timeout.
 * Maps to LFP-SANCTIONS-002 (HTTP 503 Service Unavailable).
 *
 * Behaviour:
 *   - If {@code aml.screening-enabled=true}  → fail-closed → transaction is blocked.
 *   - If {@code aml.screening-enabled=false} → fail-open  → this exception is suppressed.
 */
public class ScreeningTimeoutException extends RuntimeException {

    public ScreeningTimeoutException(long elapsedMs) {
        super("Sanctions screening timed out after " + elapsedMs + " ms");
    }
}
