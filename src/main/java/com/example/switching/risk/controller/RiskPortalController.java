package com.example.switching.risk.controller;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/risk")
@PreAuthorize("hasAnyRole('SYSTEM_ADMIN','OPS_ADMIN','RISK_OFFICER','AUDITOR','READ_ONLY')")
public class RiskPortalController {
    private final JdbcTemplate jdbc;

    public RiskPortalController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping("/rules")
    public Map<String, Object> rules() {
        List<Map<String, Object>> items = jdbc.query("""
                SELECT id, rule_code, description, window_seconds, max_count, max_amount,
                       currency, action, enabled, priority, updated_by, updated_at
                  FROM fraud_velocity_rule
                 ORDER BY priority, rule_code
                """, (rs, row) -> Map.<String, Object>of(
                    "ruleId", rs.getObject("id", UUID.class).toString(),
                    "ruleName", rs.getString("rule_code"),
                    "description", rs.getString("description"),
                    "threshold", threshold(rs.getObject("max_count"), rs.getObject("max_amount"), rs.getInt("window_seconds"), rs.getString("currency")),
                    "actionOnBreach", rs.getString("action"),
                    "enabled", rs.getBoolean("enabled"),
                    "priority", rs.getInt("priority"),
                    "updatedBy", rs.getString("updated_by") == null ? "" : rs.getString("updated_by"),
                    "updatedAt", rs.getTimestamp("updated_at").toInstant().toString()));
        return Map.of("items", items, "totalItems", items.size(), "returnedItems", items.size());
    }

    @PostMapping("/rules/{id}/enable")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','RISK_OFFICER')")
    public ResponseEntity<Map<String, Object>> enable(@PathVariable UUID id, Authentication auth) {
        return updateRule(id, true, auth);
    }

    @PostMapping("/rules/{id}/disable")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','RISK_OFFICER')")
    public ResponseEntity<Map<String, Object>> disable(@PathVariable UUID id, Authentication auth) {
        return updateRule(id, false, auth);
    }

    @GetMapping("/scores/distribution")
    public Map<String, Object> distribution(
            @RequestParam(required = false) Instant since,
            @RequestParam(required = false) Instant until) {
        Instant windowEnd = until == null ? Instant.now() : until;
        Instant windowStart = since == null ? windowEnd.minusSeconds(24 * 60 * 60) : since;
        if (!windowStart.isBefore(windowEnd)) throw new IllegalArgumentException("since must be before until");

        Map<Integer, Long> counts = new LinkedHashMap<>();
        for (int bucket = 0; bucket < 10; bucket++) counts.put(bucket, 0L);
        jdbc.query("""
                SELECT LEAST(FLOOR(score * 100 / 10)::integer, 9) AS bucket, COUNT(*) AS total
                  FROM fraud_scores
                 WHERE scored_at >= ? AND scored_at < ?
                 GROUP BY bucket
                """, (org.springframework.jdbc.core.RowCallbackHandler) rs -> counts.put(rs.getInt("bucket"), rs.getLong("total")),
                java.sql.Timestamp.from(windowStart), java.sql.Timestamp.from(windowEnd));

        List<Map<String, Object>> items = new ArrayList<>();
        long total = 0;
        for (int bucket = 0; bucket < 10; bucket++) {
            long count = counts.get(bucket);
            total += count;
            items.add(Map.of("range", bucket == 0 ? "0-10" : (bucket * 10 + 1) + "-" + ((bucket + 1) * 10), "count", count));
        }
        return Map.of("items", items, "totalScored", total, "windowStart", windowStart.toString(), "windowEnd", windowEnd.toString());
    }

    private ResponseEntity<Map<String, Object>> updateRule(UUID id, boolean enabled, Authentication auth) {
        int updated = jdbc.update("UPDATE fraud_velocity_rule SET enabled=?, updated_by=?, updated_at=now() WHERE id=?",
                enabled, auth == null ? "unknown" : auth.getName(), id);
        return updated == 1 ? ResponseEntity.ok(Map.of("ruleId", id.toString(), "enabled", enabled)) : ResponseEntity.notFound().build();
    }

    private static String threshold(Object maxCount, Object maxAmount, int seconds, String currency) {
        if (maxCount != null) return maxCount + " txns/" + seconds + "s";
        if (maxAmount != null) return currency + " " + maxAmount + "/" + seconds + "s";
        return "No threshold";
    }
}
