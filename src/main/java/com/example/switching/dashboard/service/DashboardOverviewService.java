package com.example.switching.dashboard.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.switching.dashboard.dto.DashboardOverviewResponse;
import com.example.switching.dashboard.dto.HourlyTrendPoint;
import com.example.switching.dashboard.dto.PoolHealthSummary;
import com.example.switching.dashboard.dto.StatusCountResponse;
import com.example.switching.inquiry.enums.InquiryStatus;
import com.example.switching.inquiry.repository.InquiryRepository;
import com.example.switching.outbox.enums.OutboxStatus;
import com.example.switching.outbox.repository.OutboxEventRepository;
import com.example.switching.transfer.enums.TransferStatus;
import com.example.switching.transfer.repository.TransferRepository;

/**
 * Builds the enriched operations dashboard overview.
 *
 * <ul>
 *   <li>Today's totals + success rate — from {@code daily_transaction_summary}</li>
 *   <li>24-hour hourly trend — from {@code hourly_transaction_summary}</li>
 *   <li>Pool health snapshot — from {@code psp_pools}</li>
 *   <li>Open dispute + pending outbox counts</li>
 *   <li>Legacy per-status counts (backwards compatible)</li>
 * </ul>
 */
@Service
public class DashboardOverviewService {

    private final InquiryRepository     inquiryRepository;
    private final TransferRepository    transferRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final JdbcTemplate          jdbcTemplate;

    public DashboardOverviewService(InquiryRepository inquiryRepository,
                                    TransferRepository transferRepository,
                                    OutboxEventRepository outboxEventRepository,
                                    JdbcTemplate jdbcTemplate) {
        this.inquiryRepository     = inquiryRepository;
        this.transferRepository    = transferRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.jdbcTemplate          = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public DashboardOverviewResponse getOverview() {
        DashboardOverviewResponse resp = new DashboardOverviewResponse();
        resp.setGeneratedAt(LocalDateTime.now());

        // ── Today's aggregation ──────────────────────────────────────────────
        Map<String, Object> today = jdbcTemplate.queryForMap("""
                SELECT COALESCE(SUM(total_count),    0) AS total_count,
                       COALESCE(SUM(settled_count),  0) AS settled_count,
                       COALESCE(SUM(rejected_count), 0) AS rejected_count,
                       COALESCE(SUM(total_amount),   0) AS total_amount,
                       COALESCE(SUM(settled_amount), 0) AS settled_amount
                  FROM daily_transaction_summary
                 WHERE summary_date = CURRENT_DATE
                """);

        long totalCount   = toLong(today.get("total_count"));
        long settledCount = toLong(today.get("settled_count"));

        resp.setTodayTotalCount(totalCount);
        resp.setTodaySettledCount(settledCount);
        resp.setTodayRejectedCount(toLong(today.get("rejected_count")));
        resp.setTodayTotalVolumeLak(toBigDecimal(today.get("total_amount")));
        resp.setTodaySettledVolumeLak(toBigDecimal(today.get("settled_amount")));
        // Round to 2 decimal places: e.g. 99.99 not 99.990000001
        resp.setTodaySuccessRate(totalCount > 0
                ? Math.round(settledCount * 10_000.0 / totalCount) / 100.0
                : 0.0);

        // ── 24-hour hourly trend ─────────────────────────────────────────────
        List<Map<String, Object>> hourlyRows = jdbcTemplate.queryForList("""
                SELECT summary_date::text  AS summary_date,
                       hour_of_day,
                       SUM(total_count)    AS total_count,
                       SUM(settled_count)  AS settled_count,
                       SUM(total_amount)   AS total_amount
                  FROM hourly_transaction_summary
                 WHERE (summary_date = CURRENT_DATE - 1
                        AND hour_of_day >= EXTRACT(HOUR FROM NOW())::int)
                    OR  summary_date = CURRENT_DATE
                 GROUP BY summary_date, hour_of_day
                 ORDER BY summary_date, hour_of_day
                 LIMIT 24
                """);

        resp.setHourlyTrend(hourlyRows.stream()
                .map(row -> new HourlyTrendPoint(
                        ((Number) row.get("hour_of_day")).intValue(),
                        (String) row.get("summary_date"),
                        toLong(row.get("total_count")),
                        toLong(row.get("settled_count")),
                        toBigDecimal(row.get("total_amount"))))
                .toList());

        // ── Pool health ──────────────────────────────────────────────────────
        Map<String, Object> pool = jdbcTemplate.queryForMap("""
                SELECT COUNT(*)                                                           AS total_pools,
                       COALESCE(SUM(available_balance), 0)                               AS total_available,
                       COUNT(*) FILTER (WHERE available_balance < minimum_balance)       AS low_balance_count
                  FROM psp_pools
                """);

        resp.setPoolHealth(new PoolHealthSummary(
                toLong(pool.get("total_pools")),
                toLong(pool.get("low_balance_count")),
                toBigDecimal(pool.get("total_available"))));

        // ── Active work items ────────────────────────────────────────────────
        Long openDisputes = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM disputes WHERE status IN ('OPEN','UNDER_REVIEW')", Long.class);
        resp.setOpenDisputeCount(openDisputes != null ? openDisputes : 0L);

        Long pendingOutbox = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_messages WHERE status = 'PENDING'", Long.class);
        resp.setPendingOutboxCount(pendingOutbox != null ? pendingOutbox : 0L);

        // ── Legacy status counts ─────────────────────────────────────────────
        resp.setInquiriesTotal(inquiryRepository.count());
        resp.setInquiryStatusCounts(Arrays.stream(InquiryStatus.values())
                .map(s -> new StatusCountResponse(s.name(), inquiryRepository.countByStatus(s)))
                .toList());

        resp.setTransfersTotal(transferRepository.count());
        resp.setTransferStatusCounts(Arrays.stream(TransferStatus.values())
                .map(s -> new StatusCountResponse(s.name(), transferRepository.countByStatus(s)))
                .toList());

        resp.setOutboxEventsTotal(outboxEventRepository.count());
        resp.setOutboxStatusCounts(Arrays.stream(OutboxStatus.values())
                .map(s -> new StatusCountResponse(s.name(), outboxEventRepository.countByStatus(s)))
                .toList());

        return resp;
    }

    // ── type-safe helpers (package-visible for unit tests) ────────────────────

    public static long toLong(Object value) {
        if (value == null) return 0L;
        return ((Number) value).longValue();
    }

    public static BigDecimal toBigDecimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal bd) return bd;
        return new BigDecimal(value.toString());
    }
}
