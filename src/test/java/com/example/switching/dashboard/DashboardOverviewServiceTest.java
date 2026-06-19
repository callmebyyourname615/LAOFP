package com.example.switching.dashboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import com.example.switching.dashboard.dto.DashboardOverviewResponse;
import com.example.switching.dashboard.service.DashboardOverviewService;
import com.example.switching.inquiry.enums.InquiryStatus;
import com.example.switching.inquiry.repository.InquiryRepository;
import com.example.switching.outbox.enums.OutboxStatus;
import com.example.switching.outbox.repository.OutboxEventRepository;
import com.example.switching.transfer.enums.TransferStatus;
import com.example.switching.transfer.repository.TransferRepository;

/**
 * Pure unit tests for {@link DashboardOverviewService}.
 * No Spring context — only Mockito.
 */
@ExtendWith(MockitoExtension.class)
class DashboardOverviewServiceTest {

    @Mock JdbcTemplate          jdbcTemplate;
    @Mock InquiryRepository     inquiryRepository;
    @Mock TransferRepository    transferRepository;
    @Mock OutboxEventRepository outboxEventRepository;

    @InjectMocks DashboardOverviewService service;

    // ── helpers ───────────────────────────────────────────────────────────────

    private void stubDailySummary(long total, long settled, long rejected,
                                   BigDecimal totalAmt, BigDecimal settledAmt) {
        when(jdbcTemplate.queryForMap(contains("daily_transaction_summary")))
                .thenReturn(Map.of(
                        "total_count",    total,
                        "settled_count",  settled,
                        "rejected_count", rejected,
                        "total_amount",   totalAmt,
                        "settled_amount", settledAmt));
    }

    private void stubHourlyTrend() {
        when(jdbcTemplate.queryForList(contains("hourly_transaction_summary")))
                .thenReturn(List.of());
    }

    private void stubPoolHealth(long pools, long low, BigDecimal avail) {
        when(jdbcTemplate.queryForMap(contains("psp_pools")))
                .thenReturn(Map.of(
                        "total_pools",       pools,
                        "low_balance_count", low,
                        "total_available",   avail));
    }

    private void stubWorkItems(long disputes, long outbox) {
        when(jdbcTemplate.queryForObject(contains("disputes"), eq(Long.class)))
                .thenReturn(disputes);
        when(jdbcTemplate.queryForObject(contains("outbox_messages"), eq(Long.class)))
                .thenReturn(outbox);
    }

    private void stubRepositoryCounts() {
        when(inquiryRepository.count()).thenReturn(0L);
        when(transferRepository.count()).thenReturn(0L);
        when(outboxEventRepository.count()).thenReturn(0L);
        for (InquiryStatus s  : InquiryStatus.values())  when(inquiryRepository.countByStatus(s)).thenReturn(0L);
        for (TransferStatus s : TransferStatus.values())  when(transferRepository.countByStatus(s)).thenReturn(0L);
        for (OutboxStatus s   : OutboxStatus.values())    when(outboxEventRepository.countByStatus(s)).thenReturn(0L);
    }

    // ── TC-DASH-001 ───────────────────────────────────────────────────────────

    @Test
    void getOverview_successRateCalculatedCorrectly() {
        // 80 settled out of 100 total = 80.00%
        stubDailySummary(100L, 80L, 10L,
                new BigDecimal("5000000.00"), new BigDecimal("4000000.00"));
        stubHourlyTrend();
        stubPoolHealth(3L, 0L, new BigDecimal("9000000.00"));
        stubWorkItems(2L, 5L);
        stubRepositoryCounts();

        DashboardOverviewResponse resp = service.getOverview();

        assertEquals(80.0,           resp.getTodaySuccessRate(), 0.01, "success rate must be 80%");
        assertEquals(100L,           resp.getTodayTotalCount());
        assertEquals(80L,            resp.getTodaySettledCount());
        assertEquals(10L,            resp.getTodayRejectedCount());
        assertEquals(0, new BigDecimal("5000000.00").compareTo(resp.getTodayTotalVolumeLak()));
        assertEquals(0, new BigDecimal("4000000.00").compareTo(resp.getTodaySettledVolumeLak()));
    }

    // ── TC-DASH-002 — zero-divide protection ─────────────────────────────────

    @Test
    void getOverview_noTransactionsToday_successRateIsZero() {
        stubDailySummary(0L, 0L, 0L, BigDecimal.ZERO, BigDecimal.ZERO);
        stubHourlyTrend();
        stubPoolHealth(2L, 0L, new BigDecimal("1000000.00"));
        stubWorkItems(0L, 0L);
        stubRepositoryCounts();

        DashboardOverviewResponse resp = service.getOverview();

        assertEquals(0.0, resp.getTodaySuccessRate(), 0.001, "No transactions → 0% success rate");
        assertEquals(0L,  resp.getTodayTotalCount());
    }

    // ── TC-DASH-003 — pool health ─────────────────────────────────────────────

    @Test
    void getOverview_poolHealthPopulated() {
        stubDailySummary(50L, 45L, 3L,
                new BigDecimal("2000000.00"), new BigDecimal("1800000.00"));
        stubHourlyTrend();
        stubPoolHealth(5L, 2L, new BigDecimal("12000000.00"));
        stubWorkItems(0L, 3L);
        stubRepositoryCounts();

        DashboardOverviewResponse resp = service.getOverview();

        assertNotNull(resp.getPoolHealth());
        assertEquals(5L, resp.getPoolHealth().totalPools(),        "total pools");
        assertEquals(2L, resp.getPoolHealth().lowBalanceCount(),   "low balance count");
        assertEquals(0,  new BigDecimal("12000000.00")
                .compareTo(resp.getPoolHealth().totalAvailableLak()), "total available");
    }

    // ── TC-DASH-004 — work-item counts ────────────────────────────────────────

    @Test
    void getOverview_openDisputeAndPendingOutboxCounted() {
        stubDailySummary(20L, 18L, 1L,
                new BigDecimal("1000000.00"), new BigDecimal("900000.00"));
        stubHourlyTrend();
        stubPoolHealth(2L, 0L, new BigDecimal("5000000.00"));
        stubWorkItems(7L, 12L);
        stubRepositoryCounts();

        DashboardOverviewResponse resp = service.getOverview();

        assertEquals(7L,  resp.getOpenDisputeCount(),  "open disputes");
        assertEquals(12L, resp.getPendingOutboxCount(), "pending outbox");
    }

    // ── TC-DASH-005 — 100% success rate ──────────────────────────────────────

    @Test
    void getOverview_allSettled_successRateIs100() {
        stubDailySummary(200L, 200L, 0L,
                new BigDecimal("10000000.00"), new BigDecimal("10000000.00"));
        stubHourlyTrend();
        stubPoolHealth(4L, 0L, new BigDecimal("50000000.00"));
        stubWorkItems(0L, 0L);
        stubRepositoryCounts();

        DashboardOverviewResponse resp = service.getOverview();

        assertEquals(100.0, resp.getTodaySuccessRate(), 0.01, "All settled → 100%");
    }

    // ── TC-DASH-006 — generatedAt is set ─────────────────────────────────────

    @Test
    void getOverview_generatedAtIsSet() {
        stubDailySummary(0L, 0L, 0L, BigDecimal.ZERO, BigDecimal.ZERO);
        stubHourlyTrend();
        stubPoolHealth(0L, 0L, BigDecimal.ZERO);
        stubWorkItems(0L, 0L);
        stubRepositoryCounts();

        DashboardOverviewResponse resp = service.getOverview();

        assertNotNull(resp.getGeneratedAt(), "generatedAt must be populated");
        assertTrue(resp.getGeneratedAt().isBefore(
                java.time.LocalDateTime.now().plusSeconds(1)), "generatedAt must be now");
    }

    // ── TC-DASH-007 — toLong / toBigDecimal helpers ───────────────────────────

    @Test
    void helper_toLong_handlesNullAndVariousTypes() {
        assertEquals(0L,  DashboardOverviewService.toLong(null));
        assertEquals(42L, DashboardOverviewService.toLong(42));
        assertEquals(42L, DashboardOverviewService.toLong(42L));
        assertEquals(42L, DashboardOverviewService.toLong(new BigDecimal("42")));
    }

    @Test
    void helper_toBigDecimal_handlesNullAndString() {
        assertEquals(BigDecimal.ZERO,          DashboardOverviewService.toBigDecimal(null));
        assertEquals(new BigDecimal("123.45"),  DashboardOverviewService.toBigDecimal("123.45"));
        assertEquals(new BigDecimal("500.00"),  DashboardOverviewService.toBigDecimal(new BigDecimal("500.00")));
    }
}
