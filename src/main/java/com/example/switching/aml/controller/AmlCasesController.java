package com.example.switching.aml.controller;

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
 * AML Cases read-only API — surfaces sanctions_screening_results as compliance cases.
 *
 * Endpoint:
 *   GET /api/aml/cases?outcome=&listType=&limit=&offset=
 *     outcome  : BLOCKED | MANUAL_REVIEW | CLEAR    (default: BLOCKED,MANUAL_REVIEW)
 *     listType : BOL | OFAC | UN                    (optional)
 *     limit    : 1–500                              (default 100)
 *     offset   : ≥0                                 (default 0)
 *
 * Response shape:
 *   { items: [ {caseId, txnId, screenedAt, outcome, matchScore, matchEntity, listType,
 *               debtorName, creditorName, screenedBy, screeningMs} ],
 *     totalItems, returnedItems }
 */
@RestController
@RequestMapping("/api/aml/cases")
@PreAuthorize("hasAnyRole('OPS', 'ADMIN', 'RISK_OFFICER', 'AUDITOR')")
public class AmlCasesController {

    private static final String LIST_SQL = """
            SELECT screen_id, txn_id, screened_at, match_score, match_entity, list_type,
                   outcome, screening_ms, debtor_name, creditor_name, screened_by
            FROM sanctions_screening_results
            WHERE outcome = ANY(?)
              AND (?::text IS NULL OR list_type = ?)
            ORDER BY screened_at DESC
            LIMIT ? OFFSET ?
            """;

    private static final String COUNT_SQL = """
            SELECT COUNT(*)
            FROM sanctions_screening_results
            WHERE outcome = ANY(?)
              AND (?::text IS NULL OR list_type = ?)
            """;

    private final JdbcTemplate jdbcTemplate;

    public AmlCasesController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestParam(required = false, defaultValue = "BLOCKED,MANUAL_REVIEW") String outcome,
            @RequestParam(required = false) String listType,
            @RequestParam(required = false, defaultValue = "100") int limit,
            @RequestParam(required = false, defaultValue = "0") int offset) {

        int cappedLimit = Math.min(Math.max(limit, 1), 500);
        int safeOffset = Math.max(offset, 0);
        String[] outcomes = outcome.toUpperCase().split(",");
        String listFilter = (listType == null || listType.isBlank()) ? null : listType.toUpperCase();

        List<Map<String, Object>> rows = jdbcTemplate.query(
                LIST_SQL,
                (ps) -> {
                    ps.setArray(1, ps.getConnection().createArrayOf("VARCHAR", outcomes));
                    ps.setString(2, listFilter);
                    ps.setString(3, listFilter);
                    ps.setInt(4, cappedLimit);
                    ps.setInt(5, safeOffset);
                },
                (rs, rowNum) -> Map.<String, Object>ofEntries(
                        Map.entry("caseId",        rs.getLong("screen_id")),
                        Map.entry("txnId",         rs.getString("txn_id")),
                        Map.entry("screenedAt",    rs.getTimestamp("screened_at").toInstant().toString()),
                        Map.entry("outcome",       rs.getString("outcome")),
                        Map.entry("matchScore",    rs.getBigDecimal("match_score") == null ? 0 : rs.getBigDecimal("match_score")),
                        Map.entry("matchEntity",   rs.getString("match_entity") == null ? "" : rs.getString("match_entity")),
                        Map.entry("listType",      rs.getString("list_type") == null ? "" : rs.getString("list_type")),
                        Map.entry("debtorName",    rs.getString("debtor_name") == null ? "" : rs.getString("debtor_name")),
                        Map.entry("creditorName",  rs.getString("creditor_name") == null ? "" : rs.getString("creditor_name")),
                        Map.entry("screenedBy",    rs.getString("screened_by")),
                        Map.entry("screeningMs",   rs.getInt("screening_ms"))));

        Long total = jdbcTemplate.query(
                COUNT_SQL,
                (ps) -> {
                    ps.setArray(1, ps.getConnection().createArrayOf("VARCHAR", outcomes));
                    ps.setString(2, listFilter);
                    ps.setString(3, listFilter);
                },
                (rs) -> rs.next() ? rs.getLong(1) : 0L);

        return ResponseEntity.ok(Map.of(
                "items",         rows,
                "totalItems",    total == null ? 0 : total,
                "returnedItems", rows.size()));
    }
}
