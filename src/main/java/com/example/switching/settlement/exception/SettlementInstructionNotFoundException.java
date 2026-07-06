package com.example.switching.settlement.exception;

public class SettlementInstructionNotFoundException extends RuntimeException {
    public SettlementInstructionNotFoundException(String instructionRef) {
        super("Settlement instruction not found: " + instructionRef);
    }
}
