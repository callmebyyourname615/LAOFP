package com.example.switching.risk.dto;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Fraud scoring result returned by {@link com.example.switching.risk.service.FraudScoringService#score}.
 */
public class FraudScore {

    /** Normalised score 0.0000–1.0000. */
    private final BigDecimal score;

    /** LOW | MEDIUM | HIGH | CRITICAL */
    private final String riskTier;

    /** Signals that contributed to the score, e.g. {"velocity_hit": true, "amount_anomaly": false}. */
    private final Map<String, Object> signals;

    /** ALLOW | FLAG | BLOCK */
    private final String actionTaken;

    public FraudScore(BigDecimal score, String riskTier,
                      Map<String, Object> signals, String actionTaken) {
        this.score = score;
        this.riskTier = riskTier;
        this.signals = signals;
        this.actionTaken = actionTaken;
    }

    public BigDecimal getScore()             { return score; }
    public String getRiskTier()              { return riskTier; }
    public Map<String, Object> getSignals()  { return signals; }
    public String getActionTaken()           { return actionTaken; }

    public boolean isBlocked() { return "BLOCK".equals(actionTaken); }
}
