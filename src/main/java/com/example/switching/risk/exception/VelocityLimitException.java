package com.example.switching.risk.exception;

/**
 * Thrown when a PSP exceeds a velocity limit (count or amount).
 * Maps to LFP-RISK-002 (HTTP 429 Too Many Requests).
 */
public class VelocityLimitException extends RuntimeException {

    private final String checkType;
    private final String pspId;

    public VelocityLimitException(String pspId, String checkType, double current, double limit) {
        super("Velocity limit exceeded for PSP " + pspId
                + " [" + checkType + "]: current=" + current + ", limit=" + limit);
        this.checkType = checkType;
        this.pspId = pspId;
    }

    public String getCheckType() { return checkType; }
    public String getPspId() { return pspId; }
}
