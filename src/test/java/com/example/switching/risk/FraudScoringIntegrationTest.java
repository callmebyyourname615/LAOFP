package com.example.switching.risk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.example.switching.AbstractIntegrationTest;
import com.example.switching.risk.dto.FraudScore;
import com.example.switching.risk.service.FraudScoringService;

/**
 * Integration tests for {@link FraudScoringService}.
 *
 * TC-FRAUD-001 — Low-risk transfer → score < 0.40, action = ALLOW
 * TC-FRAUD-002 — Round-number signal → score includes round-number component
 * TC-FRAUD-003 — Velocity hit + round number → score ≥ 0.75 → BLOCK
 * TC-FRAUD-004 — Fraud score persisted to fraud_scores table
 * TC-FRAUD-005 — fraud-scoring-enabled=false → noScore / ALLOW (config test, stubbed)
 */
class FraudScoringIntegrationTest extends AbstractIntegrationTest {

    @Autowired FraudScoringService fraudScoringService;
    @Autowired JdbcTemplate        jdbcTemplate;

    private static final String PSP_SENDER   = "FRAUD-TEST-SRC";
    private static final String PSP_RECEIVER = "FRAUD-TEST-DST";

    @BeforeEach
    void seedParticipants() {
        jdbcTemplate.update("""
                INSERT INTO participants (bank_code, bank_name, status, created_at)
                VALUES (?, ?, 'ACTIVE', NOW()),
                       (?, ?, 'ACTIVE', NOW())
                ON CONFLICT (bank_code) DO NOTHING
                """, PSP_SENDER, "Fraud Src PSP", PSP_RECEIVER, "Fraud Dst PSP");
    }

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("DELETE FROM fraud_scores WHERE txn_id LIKE 'TC-FRAUD-%'");
        jdbcTemplate.update("DELETE FROM velocity_checks WHERE psp_id IN (?, ?)",
                PSP_SENDER, PSP_RECEIVER);
        jdbcTemplate.update("DELETE FROM participants WHERE bank_code IN (?, ?)",
                PSP_SENDER, PSP_RECEIVER);
    }

    // ── TC-FRAUD-001 ─────────────────────────────────────────────────────────

    @Test
    void tc_fraud_001_lowRisk_actionIsAllow() {
        FraudScore result = fraudScoringService.score(
                "TC-FRAUD-001",
                new BigDecimal("5000"),   // small, non-round amount
                PSP_SENDER,
                PSP_RECEIVER);

        assertNotNull(result);
        assertFalse(result.isBlocked(), "Low-risk transfer should not be blocked");
        assertEquals("ALLOW", result.getActionTaken());
        assertTrue(result.getScore().doubleValue() < 0.40,
                "Low-risk score should be < 0.40 but was: " + result.getScore());
    }

    // ── TC-FRAUD-002 ─────────────────────────────────────────────────────────

    @Test
    void tc_fraud_002_roundNumber_signalPresent() {
        // 5,000,000 LAK — divisible by 1,000,000 → round-number signal
        FraudScore result = fraudScoringService.score(
                "TC-FRAUD-002",
                new BigDecimal("5000000"),
                PSP_SENDER,
                PSP_RECEIVER);

        assertNotNull(result);
        assertNotNull(result.getSignals());
        Object roundNumber = result.getSignals().get("round_number");
        assertNotNull(roundNumber, "round_number signal should be present");
        assertTrue(Boolean.parseBoolean(roundNumber.toString()),
                "round_number signal should be TRUE for 5,000,000 LAK");
    }

    // ── TC-FRAUD-003 ─────────────────────────────────────────────────────────

    @Test
    void tc_fraud_003_velocityHitAndRoundNumber_blocked() {
        // Pre-seed velocity_checks so velocity_hit = TRUE (hourly count at limit)
        java.time.LocalDateTime hourStart = java.time.LocalDateTime.now()
                .truncatedTo(java.time.temporal.ChronoUnit.HOURS);
        java.time.LocalDateTime hourEnd = hourStart.plusHours(1);

        jdbcTemplate.update("""
                INSERT INTO velocity_checks
                    (psp_id, check_type, window_start, window_end, current_value, limit_value, breached, last_updated_at)
                VALUES (?, 'COUNT_HOURLY', ?, ?, 100, 100, FALSE, NOW())
                ON CONFLICT (psp_id, check_type, window_start) DO UPDATE
                    SET current_value = 100, limit_value = 100
                """, PSP_SENDER, hourStart, hourEnd);

        // Round-number amount (5M LAK) + velocity hit → score = 0.60 + 0.15 = 0.75 → BLOCK
        FraudScore result = fraudScoringService.score(
                "TC-FRAUD-003",
                new BigDecimal("5000000"),
                PSP_SENDER,
                PSP_RECEIVER);

        assertTrue(result.isBlocked(),
                "Velocity hit + round-number should push score ≥ 0.75 → BLOCK");
        assertEquals("BLOCK", result.getActionTaken());
        assertTrue(result.getScore().doubleValue() >= 0.75,
                "Score should be ≥ 0.75 but was: " + result.getScore());
    }

    // ── TC-FRAUD-004 ─────────────────────────────────────────────────────────

    @Test
    void tc_fraud_004_score_persistedToFraudScoresTable() {
        fraudScoringService.score(
                "TC-FRAUD-004",
                new BigDecimal("1000"),
                PSP_SENDER,
                PSP_RECEIVER);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT txn_id, risk_tier, action_taken FROM fraud_scores WHERE txn_id = ?",
                "TC-FRAUD-004");
        assertEquals(1, rows.size(), "One fraud_scores row should be persisted");
        assertEquals("TC-FRAUD-004", rows.get(0).get("txn_id"));
        assertNotNull(rows.get(0).get("risk_tier"));
        assertNotNull(rows.get(0).get("action_taken"));
    }
}
