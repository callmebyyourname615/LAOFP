package com.example.switching.aml.sanctions.provider;

public class SanctionsProviderException extends RuntimeException {
    public SanctionsProviderException(String message) {
        super(message);
    }

    public SanctionsProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
