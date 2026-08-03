package com.example.switching.security.controller;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.switching.common.util.MaskingUtil;

@RestController
@RequestMapping("/api/admin/security-events")
@PreAuthorize("hasAnyRole('SYSTEM_ADMIN','AUDITOR')")
public class SecurityEventsController {
    private static final int MAX_LIMIT = 500;
    private final JdbcTemplate jdbc;

    public SecurityEventsController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping
    public Map<String, Object> list(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) Instant since,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        List<Object> params = new ArrayList<>();
        List<String> clauses = new ArrayList<>();
        clauses.add("(event_type LIKE '%LOGIN%' OR event_type LIKE '%MFA%' OR event_type LIKE '%SESSION%' OR event_type LIKE '%KEY%' OR event_type LIKE '%BREAK_GLASS%' OR event_type LIKE '%PRIVILEGE%')");
        if (type != null && !type.isBlank()) {
            String[] types = type.split(",");
            List<String> placeholders = new ArrayList<>();
            for (String value : types) { placeholders.add("?"); params.add(value.trim().toUpperCase()); }
            clauses.add("event_type IN (" + String.join(",", placeholders) + ")");
        }
        if (actor != null && !actor.isBlank()) { clauses.add("actor = ?"); params.add(actor.trim()); }
        params.add(Timestamp.from(since == null ? Instant.now().minusSeconds(7 * 24 * 60 * 60) : since));
        clauses.add("created_at >= ?");
        String where = " WHERE " + String.join(" AND ", clauses);
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs" + where, Long.class, params.toArray());
        List<Object> listParams = new ArrayList<>(params);
        listParams.add(Math.min(Math.max(limit, 1), MAX_LIMIT));
        listParams.add(Math.max(offset, 0));
        List<Map<String, Object>> items = jdbc.query("SELECT id,event_type,actor,payload,created_at FROM audit_logs" + where + " ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?",
                (rs, row) -> Map.<String, Object>of(
                        "eventId", Long.toString(rs.getLong("id")),
                        "type", rs.getString("event_type"),
                        "actor", rs.getString("actor"),
                        "ip", "",
                        "timestamp", rs.getTimestamp("created_at").toInstant().toString(),
                        "outcome", outcome(rs.getString("event_type")),
                        "details", maskedPayload(rs.getString("payload"))), listParams.toArray());
        return Map.of("items", items, "totalItems", total == null ? 0 : total, "returnedItems", items.size());
    }

    private static String outcome(String eventType) {
        return eventType.contains("FAILED") || eventType.contains("DENIED") ? "FAILED" : "SUCCESS";
    }

    private static String maskedPayload(String payload) {
        String masked = MaskingUtil.maskAccountFieldsInText(payload);
        return masked == null ? "" : masked;
    }
}
