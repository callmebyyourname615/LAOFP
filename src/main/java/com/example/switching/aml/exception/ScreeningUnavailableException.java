package com.example.switching.aml.exception;

/** Raised when sanctions screening cannot produce a trustworthy decision. */
public class ScreeningUnavailableException extends RuntimeException {
    public ScreeningUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
