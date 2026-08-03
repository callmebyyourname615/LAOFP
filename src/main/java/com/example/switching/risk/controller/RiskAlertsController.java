package com.example.switching.risk.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Risk Alerts read-only API — surfaces high-risk fraud_scores rows as alerts.
 *
 * Endpoint:
 *   GET /api/risk/alerts?severity=&status=&limit=&offset=
 *     severity : HIGH | CRITICAL | MEDIUM   (default: HIGH,CRITICAL)
 *     status   : FLAG | BLOCK | ALLOW       (optional; further narrows action_taken)
 *     limit    : 1–500                      (default 100)
 *     offset   : ≥0                         (default 0)
 *
 * Response shape:
 *   { items: [ {alertId, txnId, timestamp, severity, action, score, signals,
 *               sendingPspId, receivingPspId, amount} ], totalItems, returnedItems }
 */
@RestController
@RequestMapping("/api/risk/alerts")
@PreAuthorize("hasAnyRole('OPS', 'ADMIN', 'RISK_OFFICER', 'AUDITOR', 'READ_ONLY')")
public class RiskAlertsController {

    private static final String LIST_SQL = """
            SELECT score_id, txn_id, scored_at, score, risk_tier, action_taken, signals,
                   sending_psp_id, receiving_psp_id, amount
            FROM fraud_scores
            WHERE risk_tier = ANY(?)
              AND (?::text IS NULL OR action_taken = ?)
            ORDER BY scored_at DESC
            LIMIT ? OFFSET ?
            """;

    private static final String COUNT_SQL = """
            SELECT COUNT(*)
            FROM fraud_scores
            WHERE risk_tier = ANY(?)
              AND (?::text IS NULL OR action_taken = ?)
            """;

    private final JdbcTemplate jdbcTemplate;

    public RiskAlertsController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestParam(required = false, defaultValue = "HIGH,CRITICAL") String severity,
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "100") int limit,
            @RequestParam(required = false, defaultValue = "0") int offset) {

        int cappedLimit = Math.min(Math.max(limit, 1), 500);
        int safeOffset = Math.max(offset, 0);
        String[] severities = severity.toUpperCase().split(",");
        String actionFilter = (status == null || status.isBlank()) ? null : status.toUpperCase();

        List<Map<String, Object>> rows = jdbcTemplate.query(
                LIST_SQL,
                (ps) -> {
                    ps.setArray(1, ps.getConnection().createArrayOf("VARCHAR", severities));
                    ps.setString(2, actionFilter);
                    ps.setString(3, actionFilter);
                    ps.setInt(4, cappedLimit);
                    ps.setInt(5, safeOffset);
                },
                (rs, rowNum) -> Map.<String, Object>ofEntries(
                        Map.entry("alertId",         rs.getLong("score_id")),
                        Map.entry("txnId",           rs.getString("txn_id")),
                        Map.entry("timestamp",       rs.getTimestamp("scored_at").toInstant().toString()),
                        Map.entry("severity",        rs.getString("risk_tier")),
                        Map.entry("action",          rs.getString("action_taken")),
                        Map.entry("score",           rs.getBigDecimal("score")),
                        Map.entry("signals",         rs.getString("signals") == null ? "" : rs.getString("signals")),
                        Map.entry("sendingPspId",    rs.getString("sending_psp_id") == null ? "" : rs.getString("sending_psp_id")),
                        Map.entry("receivingPspId",  rs.getString("receiving_psp_id") == null ? "" : rs.getString("receiving_psp_id")),
                        Map.entry("amount",          rs.getBigDecimal("amount") == null ? 0 : rs.getBigDecimal("amount"))));

        Long total = jdbcTemplate.query(
                COUNT_SQL,
                (ps) -> {
                    ps.setArray(1, ps.getConnection().createArrayOf("VARCHAR", severities));
                    ps.setString(2, actionFilter);
                    ps.setString(3, actionFilter);
                },
                (rs) -> rs.next() ? rs.getLong(1) : 0L);

        return ResponseEntity.ok(Map.of(
                "items",         rows,
                "totalItems",    total == null ? 0 : total,
                "returnedItems", rows.size()));
    }
}
