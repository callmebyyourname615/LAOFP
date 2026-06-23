package com.example.switching.aml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.example.switching.AbstractIntegrationTest;
import com.example.switching.aml.dto.ScreeningResult;
import com.example.switching.aml.exception.SanctionsBlockException;
import com.example.switching.aml.service.SanctionsListSyncService;
import com.example.switching.aml.service.SanctionsScreeningService;

/**
 * Integration tests for {@link SanctionsScreeningService}.
 *
 * TC-AML-001 — Known OFAC name → BLOCKED + STR created + SanctionsBlockException thrown
 * TC-AML-002 — Clean name → CLEAR, no STR
 * TC-AML-003 — BLOCKED → sanctions_screening_results row persisted
 * TC-AML-004 — CLEAR → sanctions_screening_results row persisted with CLEAR outcome
 * TC-AML-005 — screening-enabled=false → CLEAR without DB check (config-only test, skipped here)
 */
class SanctionsScreeningIntegrationTest extends AbstractIntegrationTest {

    @Autowired SanctionsScreeningService screeningService;
    @Autowired SanctionsListSyncService  syncService;
    @Autowired JdbcTemplate jdbcTemplate;

    private static final String KNOWN_BLOCKED = "OSAMA AL-TERROR";
    private static final String CLEAN_NAME    = "SOMCHIT CHANTHAVONG";

    @BeforeEach
    void seedSanctionsList() {
        // Seed one blocked entity into the OFAC list
        syncService.seedTestEntry(KNOWN_BLOCKED, "OFAC", "PERSON", "TEST-SEED");
        Integer seeded = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sanctions_lists WHERE source_ref = 'TEST-SEED' AND provider_uid IS NOT NULL",
                Integer.class);
        assertEquals(1, seeded, "Sanctions test seed must always populate provider_uid");
    }

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("DELETE FROM sanctions_screening_results WHERE txn_id LIKE 'TC-AML-%'");
        jdbcTemplate.update("DELETE FROM str_reports WHERE txn_id LIKE 'TC-AML-%'");
        jdbcTemplate.update("DELETE FROM sanctions_lists WHERE source_ref = 'TEST-SEED'");
    }

    // ── TC-AML-001 ────────────────────────────────────────────────────────────

    @Test
    void tc_aml_001_knownOFACName_throwsSanctionsBlockException() {
        SanctionsBlockException ex = assertThrows(SanctionsBlockException.class,
                () -> screeningService.screen("TC-AML-001", KNOWN_BLOCKED, CLEAN_NAME));

        assertNotNull(ex.getMatchedEntity(), "Matched entity must be set");
        assertNotNull(ex.getListType(), "List type must be set");
        assertEquals("OFAC", ex.getListType(), "Should be OFAC list");
    }

    // ── TC-AML-002 ────────────────────────────────────────────────────────────

    @Test
    void tc_aml_002_cleanName_returnsClear() {
        ScreeningResult result = screeningService.screen("TC-AML-002", CLEAN_NAME, CLEAN_NAME);

        assertTrue(result.isClear(), "Unmatched name should be CLEAR");
        assertEquals("CLEAR", result.getOutcome());
    }

    // ── TC-AML-003 ────────────────────────────────────────────────────────────

    @Test
    void tc_aml_003_blocked_screeningResultPersistedToDb() {
        // BLOCKED throws — ignore the exception, just verify DB state
        try {
            screeningService.screen("TC-AML-003", KNOWN_BLOCKED, CLEAN_NAME);
        } catch (SanctionsBlockException ignored) {
            // expected
        }

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT outcome, list_type, match_entity FROM sanctions_screening_results WHERE txn_id = ?",
                "TC-AML-003");
        assertEquals(1, rows.size(), "One screening result row should be persisted");
        assertEquals("BLOCKED", rows.get(0).get("outcome"));
        assertEquals("OFAC",    rows.get(0).get("list_type"));
    }

    // ── TC-AML-004 ────────────────────────────────────────────────────────────

    @Test
    void tc_aml_004_clear_screeningResultPersistedWithClearOutcome() {
        screeningService.screen("TC-AML-004", CLEAN_NAME, CLEAN_NAME);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT outcome FROM sanctions_screening_results WHERE txn_id = ?",
                "TC-AML-004");
        assertEquals(1, rows.size(), "One screening result row should be persisted");
        assertEquals("CLEAR", rows.get(0).get("outcome"));
    }

    // ── TC-AML-005 ────────────────────────────────────────────────────────────

    @Test
    void tc_aml_005_blocked_strReportCreated() {
        // A BLOCKED hit should trigger an STR row (via StrGenerationService.generateStrQuietly)
        try {
            screeningService.screen("TC-AML-005", KNOWN_BLOCKED, CLEAN_NAME);
        } catch (SanctionsBlockException ignored) {
            // expected
        }

        // Give the async STR insert a moment to settle (it's fire-and-quiet)
        // In practice: STR is inserted synchronously in generateStrQuietly
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT status, matched_entity, list_type FROM str_reports WHERE txn_id = ?",
                "TC-AML-005");
        assertEquals(1, rows.size(), "One STR row should be created");
        assertEquals("PENDING_SUBMISSION", rows.get(0).get("status"));
        assertEquals("OFAC",              rows.get(0).get("list_type"));
    }
}
