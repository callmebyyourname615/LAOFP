package com.example.switching.vpa.exception;

/** LFP-3002 — An ACTIVE VPA with the same (type, value) already exists. */
public class VpaDuplicateException extends RuntimeException {
    public VpaDuplicateException(String message) {
        super(message);
    }
}
