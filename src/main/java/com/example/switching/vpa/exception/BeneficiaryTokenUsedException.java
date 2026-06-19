package com.example.switching.vpa.exception;

/** LFP-3004 — Beneficiary token was already consumed by a previous transfer initiation. */
public class BeneficiaryTokenUsedException extends RuntimeException {
    public BeneficiaryTokenUsedException(String message) {
        super(message);
    }
}
