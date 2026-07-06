package com.example.switching.settlement.exception;

public class SettlementInstructionInvalidStateException extends RuntimeException {
    public SettlementInstructionInvalidStateException(String instructionRef, String action, String currentStatus, String requiredStatus) {
        super("Settlement instruction " + instructionRef + " cannot " + action
                + " from status " + currentStatus
                + "; required " + requiredStatus);
    }
}
