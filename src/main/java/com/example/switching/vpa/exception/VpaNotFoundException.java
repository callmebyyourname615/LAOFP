package com.example.switching.vpa.exception;

/** LFP-3001 — VPA not registered or no active entry for (type, value). */
public class VpaNotFoundException extends RuntimeException {
    public VpaNotFoundException(String message) {
        super(message);
    }
}
