package com.example.switching.vpa.exception;

/** LFP-3003 — Beneficiary token has passed its 5-minute TTL or was not found. */
public class BeneficiaryTokenExpiredException extends RuntimeException {
    public BeneficiaryTokenExpiredException(String message) {
        super(message);
    }
}
