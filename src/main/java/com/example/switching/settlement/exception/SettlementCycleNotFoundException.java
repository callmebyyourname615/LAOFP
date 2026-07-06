package com.example.switching.settlement.exception;

public class SettlementCycleNotFoundException extends RuntimeException {
    public SettlementCycleNotFoundException(String cycleRef) {
        super("Settlement cycle not found: " + cycleRef);
    }

    public SettlementCycleNotFoundException(Long cycleId) {
        super("Settlement cycle not found: " + cycleId);
    }
}
