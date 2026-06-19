package com.example.switching.risk.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.example.switching.risk.config.RiskProperties;
import com.example.switching.risk.dto.FraudScore;
import com.example.switching.risk.dto.VelocityResult;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Computes a composite fraud score per transaction.
 *
 * <p>Score components:
 * <ul>
 *   <li><b>Velocity hit</b> (0.60 weight) — from {@link VelocityCheckService}</li>
 *   <li><b>Amount anomaly</b> (0.25 weight) — amount > 95th percentile for this PSP in the last 30 days</li>
 *   <li><b>Round-number signal</b> (0.15 weight) — amount divisible by 1,000,000 (common structuring pattern)</li>
 * </ul>
 *
 * <p>Final score: weighted sum, normalised to [0, 1].
 *
 * <p>Risk tiers:
 * <ul>
 *   <li>{@code [0.00, 0.40)} → LOW    → ALLOW</li>
 *   <li>{@code [0.40, 0.65)} → MEDIUM → FLAG</li>
 *   <li>{@code [0.65, 0.75)} → HIGH   → FLAG</li>
 *   <li>{@code [0.75, 1.00]} → CRITICAL → BLOCK (if {@code risk.fraud-scoring-enabled=true})</li>
 * </ul>
 */
@Service
public class FraudScoringService {

    private static final Logger log = LoggerFactory.getLogger(FraudScoringService.class);

    // Amount above which a round-number signal fires (1 M LAK)
    private static final BigDecimal ROUND_SIGNAL_THRESHOLD = new BigDecimal("1000000");

    private static final String INSERT_SQL = """
            INSERT INTO fraud_scores
                (txn_id, scored_at, score, risk_tier, signals, action_taken, sending_psp_id, receiving_psp_id, amount)
            VALUES (?, NOW(), ?, ?, ?::jsonb, ?, ?, ?, ?)
            """;

    private static final String AVG_SQL = """
            SELECT COALESCE(AVG(amount), 0)
            FROM fraud_scores
            WHERE sending_psp_id = ?
              AND scored_at >= NOW() - INTERVAL '30 days'
              AND action_taken IN ('ALLOW', 'FLAG')
            """;

    private final JdbcTemplate jdbcTemplate;
    private final RiskProperties riskProperties;
    private final VelocityCheckService velocityCheckService;
    private final ObjectMapper objectMapper;

    public FraudScoringService(JdbcTemplate jdbcTemplate,
                               RiskProperties riskProperties,
                               VelocityCheckService velocityCheckService,
                               ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.riskProperties = riskProperties;
        this.velocityCheckService = velocityCheckService;
        this.objectMapper = objectMapper;
    }

    /**
     * Score a transaction and persist the result to {@code fraud_scores}.
     *
     * @param txnId          transfer_ref or ISO message ID
     * @param amount         transaction amount in LAK
     * @param sendingPspId   originating PSP
     * @param receivingPspId destination PSP
     * @return {@link FraudScore} — caller checks {@code isBlocked()} to decide whether to reject
     */
    public FraudScore score(String txnId, BigDecimal amount,
                            String sendingPspId, String receivingPspId) {
        if (!riskProperties.isFraudScoringEnabled()) {
            return noScore(txnId);
        }

        try {
            Map<String, Object> signals = new LinkedHashMap<>();

            // Signal 1: velocity
            VelocityResult velocity = velocityCheckService.checkVelocity(sendingPspId, amount);
            boolean velocityHit = !velocity.isWithinLimits();
            signals.put("velocity_hit",    velocityHit);
            signals.put("velocity_rule",   velocity.getBreachedRule());

            // Signal 2: amount anomaly (> 2× average)
            Double avg = jdbcTemplate.queryForObject(AVG_SQL, Double.class, sendingPspId);
            double avgAmount = avg == null ? 0.0 : avg;
            double amountDouble = amount == null ? 0.0 : amount.doubleValue();
            boolean amountAnomaly = avgAmount > 0 && amountDouble > 2.0 * avgAmount;
            signals.put("amount_anomaly",  amountAnomaly);
            signals.put("avg_amount_30d",  avgAmount);

            // Signal 3: round-number structuring signal
            boolean roundNumber = amount != null
                    && amount.compareTo(ROUND_SIGNAL_THRESHOLD) > 0
                    && amount.remainder(ROUND_SIGNAL_THRESHOLD).compareTo(BigDecimal.ZERO) == 0;
            signals.put("round_number",    roundNumber);

            // Composite score: velocity(0.60) + anomaly(0.25) + round(0.15)
            double raw = (velocityHit ? 0.60 : 0.0)
                       + (amountAnomaly ? 0.25 : 0.0)
                       + (roundNumber   ? 0.15 : 0.0);
            BigDecimal compositeScore = BigDecimal.valueOf(raw).setScale(4, RoundingMode.HALF_UP);

            String riskTier   = toTier(raw);
            String action     = toAction(raw, riskProperties.getHighRiskThreshold());
            signals.put("composite_score", compositeScore);
            signals.put("risk_tier",       riskTier);

            String signalsJson = objectMapper.writeValueAsString(signals);

            jdbcTemplate.update(INSERT_SQL,
                    txnId, compositeScore, riskTier, signalsJson,
                    action, sendingPspId, receivingPspId, amount);

            log.debug("Fraud score: txn={} score={} tier={} action={}", txnId, compositeScore, riskTier, action);
            return new FraudScore(compositeScore, riskTier, signals, action);

        } catch (Exception e) {
            log.warn("Fraud scoring failed (fail-open): txn={} error={}", txnId, e.getMessage());
            return noScore(txnId);
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private FraudScore noScore(String txnId) {
        return new FraudScore(BigDecimal.ZERO, "LOW", Map.of("scoring_skipped", true), "ALLOW");
    }

    private static String toTier(double score) {
        if (score < 0.40) return "LOW";
        if (score < 0.65) return "MEDIUM";
        if (score < 0.75) return "HIGH";
        return "CRITICAL";
    }

    private static String toAction(double score, double threshold) {
        if (score >= threshold) return "BLOCK";
        if (score >= 0.40)      return "FLAG";
        return "ALLOW";
    }
}
