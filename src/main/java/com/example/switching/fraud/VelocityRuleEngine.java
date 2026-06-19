package com.example.switching.fraud;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

@Service
public class VelocityRuleEngine {
    private final JdbcTemplate jdbcTemplate;

    public VelocityRuleEngine(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public FraudDecision evaluate(String transactionReference, String participantCode, String subjectKey, BigDecimal amount) {
        List<String> matched = new ArrayList<>();
        int riskScore = 0;
        String decision = "ALLOW";
        var rules = jdbcTemplate.queryForList("""
                SELECT rule_code, action, coalesce(max_amount, 0) AS max_amount
                FROM fraud_velocity_rule
                WHERE enabled = true AND now() >= effective_from AND (effective_until IS NULL OR now() < effective_until)
                ORDER BY action DESC, rule_code
                """);
        for (var rule : rules) {
            BigDecimal maxAmount = (BigDecimal) rule.get("max_amount");
            if (maxAmount.signum() > 0 && amount.compareTo(maxAmount) > 0) {
                matched.add(String.valueOf(rule.get("rule_code")));
                riskScore = Math.max(riskScore, 80);
                decision = String.valueOf(rule.get("action"));
            }
        }
        if (matched.isEmpty()) {
            riskScore = amount.compareTo(new BigDecimal("100000000")) > 0 ? 45 : 10;
            decision = riskScore >= 50 ? "REVIEW" : "ALLOW";
        }
        String evidenceHash = sha256(transactionReference + participantCode + subjectKey + amount + matched);
        jdbcTemplate.update("""
                INSERT INTO fraud_velocity_decision(transaction_reference, participant_code, subject_key, decision, matched_rules, risk_score, evidence_hash)
                VALUES (?, ?, ?, ?, to_jsonb(string_to_array(?, ',')), ?, ?)
                ON CONFLICT(transaction_reference) DO NOTHING
                """, transactionReference, participantCode, subjectKey, decision, String.join(",", matched), riskScore, evidenceHash);
        return new FraudDecision(transactionReference, decision, riskScore, matched);
    }

    private static String sha256(String input) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to hash fraud evidence", e);
        }
    }
}
