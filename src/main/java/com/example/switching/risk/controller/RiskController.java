package com.example.switching.risk.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Risk Engine read-only API — OPS/ADMIN.
 *
 * Endpoints:
 *   GET /v1/risk/scores/{txnId}  — fraud score + signals for a transaction
 */
@RestController
@RequestMapping("${switching.api.v1-prefix}/risk")
@PreAuthorize("hasAnyRole('OPS', 'ADMIN')")
public class RiskController {

    private static final String SCORE_SQL = """
            SELECT score_id, txn_id, scored_at, score, risk_tier, signals, action_taken,
                   sending_psp_id, receiving_psp_id, amount
            FROM fraud_scores
            WHERE txn_id = ?
            ORDER BY scored_at DESC
            LIMIT 10
            """;

    private final JdbcTemplate jdbcTemplate;

    public RiskController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Retrieve fraud score(s) for a transaction reference.
     */
    @GetMapping("/scores/{txnId}")
    public ResponseEntity<List<Map<String, Object>>> getScores(@PathVariable String txnId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(SCORE_SQL, txnId);
        return ResponseEntity.ok(rows);
    }
}
