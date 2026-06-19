package com.example.switching.risk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.example.switching.AbstractIntegrationTest;
import com.example.switching.risk.dto.VelocityResult;
import com.example.switching.risk.service.VelocityCheckService;

/**
 * Integration tests for {@link VelocityCheckService}.
 *
 * TC-VEL-001 — First transfer is within limits
 * TC-VEL-002 — 101st transfer in 1 hour → COUNT_HOURLY breach
 * TC-VEL-003 — Very large amount → AMOUNT_DAILY breach
 * TC-VEL-004 — Different PSP IDs do not share counters
 */
class VelocityCheckIntegrationTest extends AbstractIntegrationTest {

    @Autowired VelocityCheckService velocityCheckService;
    @Autowired JdbcTemplate         jdbcTemplate;

    private static final String TEST_PSP_VEL  = "VEL-TEST-PSP";
    private static final String TEST_PSP_VEL2 = "VEL-TEST-PSP2";
    private static final BigDecimal AMOUNT_1K = new BigDecimal("1000");

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("DELETE FROM velocity_checks WHERE psp_id IN (?, ?)",
                TEST_PSP_VEL, TEST_PSP_VEL2);
        // Also clean up participants if we created them
        jdbcTemplate.update("DELETE FROM participants WHERE bank_code IN (?, ?)",
                TEST_PSP_VEL, TEST_PSP_VEL2);
    }

    // ── Fixture ───────────────────────────────────────────────────────────────

    private void seedParticipant(String bankCode) {
        jdbcTemplate.update("""
                INSERT INTO participants (bank_code, bank_name, status, created_at)
                VALUES (?, ?, 'ACTIVE', NOW())
                ON CONFLICT (bank_code) DO NOTHING
                """, bankCode, "Test PSP " + bankCode);
    }

    // ── TC-VEL-001 ────────────────────────────────────────────────────────────

    @Test
    void tc_vel_001_firstTransfer_withinLimits() {
        seedParticipant(TEST_PSP_VEL);

        VelocityResult result = velocityCheckService.checkVelocity(TEST_PSP_VEL, AMOUNT_1K);

        assertTrue(result.isWithinLimits(), "First transfer should be within limits");
    }

    // ── TC-VEL-002 ────────────────────────────────────────────────────────────

    @Test
    void tc_vel_002_hourlyCountBreach() {
        seedParticipant(TEST_PSP_VEL);

        // Override hourly limit via direct DB insert to simulate 100 existing transactions
        // (easier than calling the service 100 times — just pre-seed the counter)
        java.time.LocalDateTime hourStart = java.time.LocalDateTime.now()
                .truncatedTo(java.time.temporal.ChronoUnit.HOURS);
        java.time.LocalDateTime hourEnd = hourStart.plusHours(1);

        jdbcTemplate.update("""
                INSERT INTO velocity_checks
                    (psp_id, check_type, window_start, window_end, current_value, limit_value, breached, last_updated_at)
                VALUES (?, 'COUNT_HOURLY', ?, ?, 100, 100, FALSE, NOW())
                ON CONFLICT (psp_id, check_type, window_start) DO UPDATE
                    SET current_value = 100, limit_value = 100, breached = FALSE
                """, TEST_PSP_VEL, hourStart, hourEnd);

        // 101st call — should breach
        VelocityResult result = velocityCheckService.checkVelocity(TEST_PSP_VEL, AMOUNT_1K);

        assertFalse(result.isWithinLimits(), "101st transfer should breach hourly limit");
        assertEquals("COUNT_HOURLY", result.getBreachedRule());
        assertTrue(result.getCurrentValue() > 100, "Current value should exceed 100");
    }

    // ── TC-VEL-003 ────────────────────────────────────────────────────────────

    @Test
    void tc_vel_003_dailyAmountBreach() {
        seedParticipant(TEST_PSP_VEL);

        java.time.LocalDateTime dayStart = java.time.LocalDateTime.now()
                .truncatedTo(java.time.temporal.ChronoUnit.DAYS);
        java.time.LocalDateTime dayEnd = dayStart.plusDays(1);

        // Pre-seed daily amount just below limit (500M LAK), then send a transfer that would push it over
        jdbcTemplate.update("""
                INSERT INTO velocity_checks
                    (psp_id, check_type, window_start, window_end, current_value, limit_value, breached, last_updated_at)
                VALUES (?, 'AMOUNT_DAILY', ?, ?, 499999000, 500000000, FALSE, NOW())
                ON CONFLICT (psp_id, check_type, window_start) DO UPDATE
                    SET current_value = 499999000, limit_value = 500000000, breached = FALSE
                """, TEST_PSP_VEL, dayStart, dayEnd);

        // Push it over the limit with a 1,001 LAK transfer
        VelocityResult result = velocityCheckService.checkVelocity(TEST_PSP_VEL,
                new BigDecimal("2000000")); // 2M LAK → exceeds remaining 1K headroom

        assertFalse(result.isWithinLimits(), "Transfer should breach daily amount limit");
        assertEquals("AMOUNT_DAILY", result.getBreachedRule());
    }

    // ── TC-VEL-004 ────────────────────────────────────────────────────────────

    @Test
    void tc_vel_004_differentPsps_doNotShareCounters() {
        seedParticipant(TEST_PSP_VEL);
        seedParticipant(TEST_PSP_VEL2);

        java.time.LocalDateTime hourStart = java.time.LocalDateTime.now()
                .truncatedTo(java.time.temporal.ChronoUnit.HOURS);
        java.time.LocalDateTime hourEnd = hourStart.plusHours(1);

        // PSP1 is at 100/100 — next transfer should breach
        jdbcTemplate.update("""
                INSERT INTO velocity_checks
                    (psp_id, check_type, window_start, window_end, current_value, limit_value, breached, last_updated_at)
                VALUES (?, 'COUNT_HOURLY', ?, ?, 100, 100, FALSE, NOW())
                ON CONFLICT (psp_id, check_type, window_start) DO UPDATE
                    SET current_value = 100, limit_value = 100
                """, TEST_PSP_VEL, hourStart, hourEnd);

        // PSP2 should be unaffected — first transfer is within limits
        VelocityResult psp2Result = velocityCheckService.checkVelocity(TEST_PSP_VEL2, AMOUNT_1K);
        assertTrue(psp2Result.isWithinLimits(), "PSP2 should not share counters with PSP1");

        // PSP1 should breach
        VelocityResult psp1Result = velocityCheckService.checkVelocity(TEST_PSP_VEL, AMOUNT_1K);
        assertFalse(psp1Result.isWithinLimits(), "PSP1 should breach its own counter");
    }
}
