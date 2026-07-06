package com.example.switching.settlement.exception;

public class SettlementReportUnavailableException extends RuntimeException {
    public SettlementReportUnavailableException(String cycleRef, String currentStatus) {
        super("Settlement ops report is available only after STGS/RTGS confirmation. cycleRef="
                + cycleRef + ", currentStatus=" + currentStatus);
    }
}
