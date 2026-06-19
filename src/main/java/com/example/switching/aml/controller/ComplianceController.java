package com.example.switching.aml.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.jdbc.core.JdbcTemplate;

import com.example.switching.aml.service.SanctionsScreeningService;

/**
 * Compliance read-only API — BoL/ADMIN only.
 *
 * Endpoints:
 *   GET /v1/compliance/sanctions/check?name=&txnId=  — manual name screening
 *   GET /v1/compliance/str/{strId}                   — STR detail
 *   GET /v1/compliance/velocity/{pspId}              — velocity counters
 */
@RestController
@RequestMapping("${switching.api.v1-prefix}/compliance")
@PreAuthorize("hasRole('ADMIN')")
public class ComplianceController {

    private static final String STR_BY_ID_SQL = """
            SELECT str_id, txn_id, triggered_at, submitted_at, submission_ref, status,
                   retry_count, matched_entity, list_type, last_error
            FROM str_reports
            WHERE str_id = ?
            """;

    private static final String VELOCITY_SQL = """
            SELECT check_type, window_start, window_end, current_value, limit_value,
                   breached, last_updated_at
            FROM velocity_checks
            WHERE psp_id = ?
            ORDER BY check_type, window_start DESC
            LIMIT 20
            """;

    private final SanctionsScreeningService screeningService;
    private final JdbcTemplate jdbcTemplate;

    public ComplianceController(SanctionsScreeningService screeningService,
                                JdbcTemplate jdbcTemplate) {
        this.screeningService = screeningService;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Manual sanctions name check.
     *
     * @param name   the entity name to screen
     * @param txnId  optional txnId to log the check against (defaults to MANUAL-{name})
     */
    @GetMapping("/sanctions/check")
    public ResponseEntity<Map<String, Object>> sanctionsCheck(
            @RequestParam String name,
            @RequestParam(required = false, defaultValue = "") String txnId) {

        String ref = txnId.isBlank() ? "MANUAL-" + name : txnId;
        var result = screeningService.screen(ref, name, null);

        return ResponseEntity.ok(Map.of(
                "name",         name,
                "txnId",        ref,
                "outcome",      result.getOutcome(),
                "matchEntity",  result.getMatchEntity() == null ? "" : result.getMatchEntity(),
                "listType",     result.getListType()    == null ? "" : result.getListType(),
                "matchScore",   result.getMatchScore()  == null ? 0 : result.getMatchScore(),
                "screeningMs",  result.getScreeningMs()));
    }

    /**
     * STR detail by ID.
     */
    @GetMapping("/str/{strId}")
    public ResponseEntity<Map<String, Object>> getStr(@PathVariable long strId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(STR_BY_ID_SQL, strId);
        if (rows.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(rows.get(0));
    }

    /**
     * Velocity counter summary for a PSP.
     */
    @GetMapping("/velocity/{pspId}")
    public ResponseEntity<List<Map<String, Object>>> getVelocity(@PathVariable String pspId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(VELOCITY_SQL, pspId);
        return ResponseEntity.ok(rows);
    }
}
