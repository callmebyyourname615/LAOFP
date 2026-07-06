package com.example.switching.settlement.exception;

public class SettlementCycleInvalidStateException extends RuntimeException {
    public SettlementCycleInvalidStateException(String cycleRef, String action, String currentStatus, String requiredStatus) {
        super("Settlement cycle " + cycleRef + " cannot " + action
                + " from status " + currentStatus
                + "; required " + requiredStatus);
    }

    public SettlementCycleInvalidStateException(String message) {
        super(message);
    }
}
