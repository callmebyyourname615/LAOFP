package com.example.switching.aml.controller;

import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${switching.api.v1-prefix:/v1}/aml/reports")
@PreAuthorize("hasAnyRole('SYSTEM_ADMIN','OPS_ADMIN','RISK_OFFICER','AUDITOR')")
public class AmlReportsController {
    private final JdbcTemplate jdbc;
    public AmlReportsController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping
    public Map<String, Object> list(@RequestParam(defaultValue = "100") int limit,
                                    @RequestParam(defaultValue = "0") int offset) {
        int cappedLimit = Math.min(Math.max(limit, 1), 500);
        int safeOffset = Math.max(offset, 0);
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM str_reports", Long.class);
        List<Map<String, Object>> items = jdbc.query("""
                SELECT str_id,txn_id,triggered_at,submitted_at,status,submission_ref
                  FROM str_reports ORDER BY triggered_at DESC LIMIT ? OFFSET ?
                """, (rs, row) -> Map.<String, Object>of(
                    "reportId", Long.toString(rs.getLong("str_id")),
                    "name", "STR Submission Package",
                    "category", "STR",
                    "date", (rs.getTimestamp("submitted_at") == null ? rs.getTimestamp("triggered_at") : rs.getTimestamp("submitted_at")).toInstant().toString(),
                    "status", rs.getString("status"),
                    "recipient", "BoL FIU",
                    "referenceId", rs.getString("submission_ref") == null ? rs.getString("txn_id") : rs.getString("submission_ref"),
                    "artifactId", ""), cappedLimit, safeOffset);
        return Map.of("items", items, "totalItems", total == null ? 0 : total, "returnedItems", items.size());
    }
}
