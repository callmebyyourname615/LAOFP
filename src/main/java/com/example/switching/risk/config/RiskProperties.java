package com.example.switching.risk.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Risk Engine configuration properties.
 *
 * Environment variables:
 *   RISK_FRAUD_SCORING_ENABLED  (default: true)
 *   RISK_HIGH_RISK_THRESHOLD    (default: 0.75)
 *   RISK_VELOCITY_DAILY_MAX_COUNT   (default: 500)
 *   RISK_VELOCITY_HOURLY_MAX_COUNT  (default: 100)
 *   RISK_VELOCITY_DAILY_MAX_AMOUNT  (default: 500_000_000 LAK)
 */
@Component
@ConfigurationProperties(prefix = "switching.risk")
public class RiskProperties {

    /** When false, all fraud-scoring calls are skipped. */
    private boolean fraudScoringEnabled = true;

    /** Fraud score >= threshold → HIGH_RISK_TRANSACTION_BLOCKED (LFP-RISK-001). */
    private double highRiskThreshold = 0.75;

    /** Maximum transaction count per PSP per 24 h. */
    private int velocityDailyMaxCount = 500;

    /** Maximum transaction count per PSP per 1 h. */
    private int velocityHourlyMaxCount = 100;

    /** Maximum total LAK amount per PSP per 24 h (in LAK, stored as decimal). */
    private double velocityDailyMaxAmountLak = 500_000_000.0;

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public boolean isFraudScoringEnabled() { return fraudScoringEnabled; }
    public void setFraudScoringEnabled(boolean fraudScoringEnabled) { this.fraudScoringEnabled = fraudScoringEnabled; }

    public double getHighRiskThreshold() { return highRiskThreshold; }
    public void setHighRiskThreshold(double highRiskThreshold) { this.highRiskThreshold = highRiskThreshold; }

    public int getVelocityDailyMaxCount() { return velocityDailyMaxCount; }
    public void setVelocityDailyMaxCount(int velocityDailyMaxCount) { this.velocityDailyMaxCount = velocityDailyMaxCount; }

    public int getVelocityHourlyMaxCount() { return velocityHourlyMaxCount; }
    public void setVelocityHourlyMaxCount(int velocityHourlyMaxCount) { this.velocityHourlyMaxCount = velocityHourlyMaxCount; }

    public double getVelocityDailyMaxAmountLak() { return velocityDailyMaxAmountLak; }
    public void setVelocityDailyMaxAmountLak(double velocityDailyMaxAmountLak) {
        this.velocityDailyMaxAmountLak = velocityDailyMaxAmountLak;
    }
}
