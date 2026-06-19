package com.example.switching.risk.exception;

import java.math.BigDecimal;

/**
 * Thrown when the fraud score for a transaction exceeds the configured high-risk threshold.
 * Maps to LFP-RISK-001 (HTTP 422 Unprocessable Entity).
 */
public class HighRiskBlockException extends RuntimeException {

    private final BigDecimal score;
    private final String riskTier;

    public HighRiskBlockException(BigDecimal score, String riskTier) {
        super("Transaction blocked: fraud score " + score + " exceeds threshold (tier=" + riskTier + ")");
        this.score = score;
        this.riskTier = riskTier;
    }

    public BigDecimal getScore() { return score; }
    public String getRiskTier() { return riskTier; }
}
