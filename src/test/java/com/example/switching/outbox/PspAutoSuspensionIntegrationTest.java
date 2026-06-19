package com.example.switching.outbox;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.example.switching.AbstractIntegrationTest;
import com.example.switching.outbox.service.PspAutoSuspensionService;

/**
 * P10 Step 3 — FPRE PSP auto-suspension integration tests.
 *
 * <p>Tests {@link PspAutoSuspensionService#checkAndSuspend(String)} with
 * a real PostgreSQL DB (Testcontainers), seeding {@code reversal_log} rows directly:
 * <ul>
 *   <li>TC-PS-001: Below threshold → PSP stays ACTIVE, no suspension log row.</li>
 *   <li>TC-PS-002: At threshold → participant becomes INBOUND_SUSPENDED,
 *       {@code psp_suspension_log} row inserted with correct counts.</li>
 *   <li>TC-PS-003: Already INBOUND_SUSPENDED → idempotent (returns false, no second log row).</li>
 *   <li>TC-PS-004: Reversals outside the 30-min window are not counted.</li>
 *   <li>TC-PS-005: Above threshold → same result as at-threshold (suspended once).</li>
 * </ul>
 *
 * <p>Default {@code FpreProperties}: threshold = 3, window = 30 min.
 */
class PspAutoSuspensionIntegrationTest extends AbstractIntegrationTest {

    // Must match FpreProperties defaults (from application.yml)
    private static final int THRESHOLD      = 3;
    private static final int WINDOW_MINUTES = 30;

    @Autowired private PspAutoSuspensionService pspAutoSuspensionService;
    @Autowired private JdbcTemplate             jdbcTemplate;

    /**
     * Clean up test-created participants and their suspension logs after each test.
     * Required because psp_suspension_log has a FK to participants — if we leave rows
     * behind, other tests that do bulk-delete on participants (e.g. OperationsGenerateRoutes)
     * will fail with a FK violation.  Child rows must be deleted before parent rows.
     */
    @AfterEach
    void cleanUp() {
        // PS* prefix is used exclusively by this test class
        jdbcTemplate.update("DELETE FROM psp_suspension_log WHERE psp_id LIKE 'PS%'");
        jdbcTemplate.update("DELETE FROM reversal_log        WHERE destination_bank LIKE 'PS%'");
        jdbcTemplate.update("DELETE FROM participants        WHERE bank_code LIKE 'PS%'");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TC-PS-001  Below threshold → PSP not suspended
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void belowThreshold_pspNotSuspended() {
        String pspId = uniquePspId("PS1");
        seedParticipant(pspId, "ACTIVE");
        seedReversals(pspId, THRESHOLD - 1);   // 2 reversals, threshold = 3

        boolean suspended = pspAutoSuspensionService.checkAndSuspend(pspId);

        assertFalse(suspended, "PSP must not be suspended when reversal count < threshold");
        assertEquals("ACTIVE", fetchStatus(pspId));
        assertEquals(0L, countSuspensionLog(pspId),
                "No psp_suspension_log row should be created");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TC-PS-002  Exactly at threshold → suspended + log row created
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void atThreshold_pspSuspendedAndLogRowCreated() {
        String pspId = uniquePspId("PS2");
        seedParticipant(pspId, "ACTIVE");
        seedReversals(pspId, THRESHOLD);       // exactly 3

        boolean suspended = pspAutoSuspensionService.checkAndSuspend(pspId);

        assertTrue(suspended, "PSP must be suspended when reversal count >= threshold");
        assertEquals("INBOUND_SUSPENDED", fetchStatus(pspId));

        List<Map<String, Object>> logRows = jdbcTemplate.queryForList(
                "SELECT * FROM psp_suspension_log WHERE psp_id = ? ORDER BY suspension_id DESC", pspId);
        assertEquals(1, logRows.size(), "Exactly one psp_suspension_log row must be created");
        assertEquals(THRESHOLD, ((Number) logRows.get(0).get("reversal_count")).intValue(),
                "reversal_count must equal the number of triggering reversals");
        assertEquals(WINDOW_MINUTES, ((Number) logRows.get(0).get("window_minutes")).intValue(),
                "window_minutes must match the configured suspension window");
        assertNotNull(logRows.get(0).get("suspended_at"), "suspended_at must be set");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TC-PS-003  Already INBOUND_SUSPENDED → idempotent (no second log row)
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void alreadySuspended_checkAndSuspendIsIdempotent() {
        String pspId = uniquePspId("PS3");
        seedParticipant(pspId, "INBOUND_SUSPENDED");
        seedReversals(pspId, THRESHOLD + 2);   // well above threshold

        boolean suspended = pspAutoSuspensionService.checkAndSuspend(pspId);

        assertFalse(suspended, "Already-suspended PSP must not be suspended again");
        assertEquals(0L, countSuspensionLog(pspId),
                "No psp_suspension_log row should be created for already-suspended PSP");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TC-PS-004  Reversals outside the 30-min window are not counted
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void reversalsOutsideWindow_notCounted() {
        String pspId = uniquePspId("PS4");
        seedParticipant(pspId, "ACTIVE");
        // Insert THRESHOLD reversals that are 31 min old — outside the rolling window
        seedReversalsAt(pspId, THRESHOLD, LocalDateTime.now().minusMinutes(31));

        boolean suspended = pspAutoSuspensionService.checkAndSuspend(pspId);

        assertFalse(suspended, "Reversals outside the 30-min window must not contribute to suspension");
        assertEquals("ACTIVE", fetchStatus(pspId));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TC-PS-005  Above threshold → still suspended once (count matches actual count)
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void aboveThreshold_pspSuspendedWithActualReversalCount() {
        String pspId = uniquePspId("PS5");
        int extraReversals = THRESHOLD + 2;    // 5 reversals
        seedParticipant(pspId, "ACTIVE");
        seedReversals(pspId, extraReversals);

        boolean suspended = pspAutoSuspensionService.checkAndSuspend(pspId);

        assertTrue(suspended);
        assertEquals("INBOUND_SUSPENDED", fetchStatus(pspId));

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT reversal_count FROM psp_suspension_log WHERE psp_id = ?", pspId);
        assertEquals(1, rows.size());
        assertEquals(extraReversals, ((Number) rows.get(0).get("reversal_count")).intValue(),
                "reversal_count must reflect the actual number of reversals, not just the threshold");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private String uniquePspId(String prefix) {
        return prefix + "_" + System.nanoTime();
    }

    private void seedParticipant(String bankCode, String status) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("""
                INSERT INTO participants (bank_code, bank_name, status, participant_type, country, currency, created_at, updated_at)
                VALUES (?, ?, ?, 'DIRECT', 'LA', 'LAK', ?, ?)
                ON CONFLICT (bank_code) DO UPDATE SET status = EXCLUDED.status, updated_at = EXCLUDED.updated_at
                """, bankCode, bankCode + " (ps-test)", status, now, now);
    }

    /** Seed {@code count} reversal_log rows for {@code destinationBank} triggered ~1 min ago (within window). */
    private void seedReversals(String destinationBank, int count) {
        seedReversalsAt(destinationBank, count, LocalDateTime.now().minusMinutes(1));
    }

    /** Seed {@code count} reversal_log rows with a specific {@code triggeredAt} timestamp. */
    private void seedReversalsAt(String destinationBank, int count, LocalDateTime triggeredAt) {
        for (int i = 0; i < count; i++) {
            jdbcTemplate.update("""
                    INSERT INTO reversal_log (original_txn_id, destination_bank, reason, status, triggered_at, created_at)
                    VALUES (?, ?, 'MAX_RETRIES', 'COMPLETED', ?, ?)
                    """,
                    "PS-TXN-" + System.nanoTime() + "-" + i,
                    destinationBank,
                    triggeredAt,
                    LocalDateTime.now());
        }
    }

    private String fetchStatus(String bankCode) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM participants WHERE bank_code = ?", String.class, bankCode);
    }

    private long countSuspensionLog(String pspId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM psp_suspension_log WHERE psp_id = ?", Long.class, pspId);
    }
}
