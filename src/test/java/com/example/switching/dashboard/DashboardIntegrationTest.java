package com.example.switching.dashboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.example.switching.AbstractIntegrationTest;
import com.example.switching.dashboard.dto.DashboardOverviewResponse;
import com.example.switching.dashboard.service.DashboardOverviewService;

/**
 * Integration tests for the enriched dashboard (TC-DASH-INT-001 – 003).
 *
 * Seeds {@code daily_transaction_summary} and {@code psp_pools} directly;
 * does not require a running scheduler.
 */
class DashboardIntegrationTest extends AbstractIntegrationTest {

    @Autowired DashboardOverviewService service;
    @Autowired JdbcTemplate             jdbcTemplate;

    // ── TC-DASH-INT-001 — today's aggregation ────────────────────────────────

    @Test
    void getOverview_todaySummaryReflectsSeededData() {
        // Seed today's aggregation rows
        jdbcTemplate.update("""
                INSERT INTO daily_transaction_summary
                    (summary_date, source_bank, destination_bank, currency,
                     total_count, settled_count, rejected_count,
                     total_amount, settled_amount, net_amount)
                VALUES (CURRENT_DATE, 'BANK_A', 'BANK_B', 'LAK',
                        120, 100, 15,
                        6000000.00, 5000000.00, 5000000.00)
                ON CONFLICT (summary_date, source_bank, destination_bank, currency)
                DO UPDATE SET total_count    = EXCLUDED.total_count,
                              settled_count  = EXCLUDED.settled_count,
                              rejected_count = EXCLUDED.rejected_count,
                              total_amount   = EXCLUDED.total_amount,
                              settled_amount = EXCLUDED.settled_amount
                """);

        DashboardOverviewResponse resp = service.getOverview();

        assertTrue(resp.getTodayTotalCount()   >= 120, "todayTotalCount must include seeded rows");
        assertTrue(resp.getTodaySettledCount() >= 100, "todaySettledCount must include seeded rows");
        assertTrue(resp.getTodaySuccessRate()  >  0.0, "Success rate must be > 0");
        assertTrue(resp.getTodayTotalVolumeLak().compareTo(BigDecimal.ZERO) > 0,
                "Total volume must be > 0");
    }

    // ── TC-DASH-INT-002 — pool health ────────────────────────────────────────

    @Test
    void getOverview_poolHealthReflectsPspPools() {
        // Ensure at least one pool exists (seeded by V26 or previous tests)
        jdbcTemplate.update("""
                INSERT INTO psp_pools (psp_id, balance, held_amount, minimum_balance)
                VALUES ('BANK_A', 1000000.00, 0.00, 100000.00)
                ON CONFLICT (psp_id) DO UPDATE
                  SET balance = GREATEST(psp_pools.balance, 1000000.00)
                """);

        DashboardOverviewResponse resp = service.getOverview();

        assertNotNull(resp.getPoolHealth(), "poolHealth must not be null");
        assertTrue(resp.getPoolHealth().totalPools() >= 1, "At least one pool must exist");
        assertTrue(resp.getPoolHealth().totalAvailableLak().compareTo(BigDecimal.ZERO) > 0,
                "Total available must be > 0 after seeding");
    }

    // ── TC-DASH-INT-003 — response structure ─────────────────────────────────

    @Test
    void getOverview_responseStructureComplete() {
        DashboardOverviewResponse resp = service.getOverview();

        assertNotNull(resp.getGeneratedAt(),         "generatedAt must be set");
        assertNotNull(resp.getHourlyTrend(),         "hourlyTrend must not be null");
        assertNotNull(resp.getPoolHealth(),           "poolHealth must not be null");
        assertNotNull(resp.getInquiryStatusCounts(), "inquiryStatusCounts must not be null");
        assertNotNull(resp.getTransferStatusCounts(),"transferStatusCounts must not be null");
        assertNotNull(resp.getOutboxStatusCounts(),  "outboxStatusCounts must not be null");
        assertTrue(resp.getInquiryStatusCounts().size() > 0,  "Must have at least one inquiry status");
        assertTrue(resp.getTransferStatusCounts().size() > 0, "Must have at least one transfer status");
        // Success rate is always a valid percentage
        assertTrue(resp.getTodaySuccessRate() >= 0.0 && resp.getTodaySuccessRate() <= 100.0,
                "Success rate must be 0–100");
    }
}
