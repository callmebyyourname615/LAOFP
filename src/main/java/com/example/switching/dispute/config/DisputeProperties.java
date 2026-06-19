package com.example.switching.dispute.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "switching.dispute")
public class DisputeProperties {

    /** Max days after transaction for raising a dispute. Default 90. */
    private int windowDays = 90;

    /** How often SLA enforcement job runs (ms). Default 600000 = 10 min. */
    private long slaCheckIntervalMs = 600_000L;

    /** Record retention in years. Default 7. */
    private int retentionYears = 7;

    // SLA deadlines by dispute type (business days treated as calendar days)
    private int slaDaysTechnicalError = 1;
    private int slaDaysNotReceived    = 2;
    private int slaDaysWrongAmount    = 3;
    private int slaDaysFraud          = 5;   // also MERCHANT_DISPUTE, DUPLICATE_CHARGE

    // ── getters / setters ─────────────────────────────────────────────────────

    public int getWindowDays()              { return windowDays; }
    public void setWindowDays(int v)        { this.windowDays = v; }

    public long getSlaCheckIntervalMs()     { return slaCheckIntervalMs; }
    public void setSlaCheckIntervalMs(long v){ this.slaCheckIntervalMs = v; }

    public int getRetentionYears()          { return retentionYears; }
    public void setRetentionYears(int v)    { this.retentionYears = v; }

    public int getSlaDaysTechnicalError()           { return slaDaysTechnicalError; }
    public void setSlaDaysTechnicalError(int v)     { this.slaDaysTechnicalError = v; }

    public int getSlaDaysNotReceived()              { return slaDaysNotReceived; }
    public void setSlaDaysNotReceived(int v)        { this.slaDaysNotReceived = v; }

    public int getSlaDaysWrongAmount()              { return slaDaysWrongAmount; }
    public void setSlaDaysWrongAmount(int v)        { this.slaDaysWrongAmount = v; }

    public int getSlaDaysFraud()                    { return slaDaysFraud; }
    public void setSlaDaysFraud(int v)              { this.slaDaysFraud = v; }

    /** Compute SLA deadline days for the given dispute type. */
    public int slaDeadlineDays(String disputeType) {
        return switch (disputeType) {
            case "TECHNICAL_ERROR"               -> slaDaysTechnicalError;
            case "NOT_RECEIVED"                  -> slaDaysNotReceived;
            case "WRONG_AMOUNT"                  -> slaDaysWrongAmount;
            case "FRAUD", "MERCHANT_DISPUTE",
                 "DUPLICATE_CHARGE"              -> slaDaysFraud;
            default -> throw new IllegalArgumentException("Unknown dispute type: " + disputeType);
        };
    }
}
