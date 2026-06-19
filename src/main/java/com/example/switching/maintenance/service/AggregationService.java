package com.example.switching.maintenance.service;

import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Populates the three pre-aggregated summary tables from the partitioned source tables.
 *
 * <ul>
 *   <li>{@code daily_transaction_summary}  — one row per (date, source_bank, destination_bank, currency)</li>
 *   <li>{@code hourly_transaction_summary} — one row per (date, hour, source_bank, destination_bank, currency)</li>
 *   <li>{@code inquiry_daily_summary}      — one row per (date, source_bank, destination_bank)</li>
 * </ul>
 *
 * <p>All aggregations use {@code INSERT … ON CONFLICT … DO UPDATE} (upsert) so that
 * incremental (hourly) and full-day (end-of-day) runs are both idempotent.
 *
 * <p>Called from {@link AggregationScheduler} on a cron schedule.
 */
@Service
public class AggregationService {

    private static final Logger log = LoggerFactory.getLogger(AggregationService.class);

    // ── daily_transaction_summary ──────────────────────────────────────────────
    private static final String DAILY_TXN_UPSERT = """
            INSERT INTO daily_transaction_summary
                (summary_date, source_bank, destination_bank, currency,
                 total_count, settled_count, rejected_count, reversed_count,
                 total_amount, settled_amount, net_amount)
            SELECT
                business_date                                       AS summary_date,
                source_bank,
                destination_bank,
                currency,
                COUNT(*)                                            AS total_count,
                COUNT(*) FILTER (WHERE status = 'SETTLED')         AS settled_count,
                COUNT(*) FILTER (WHERE status = 'REJECTED')        AS rejected_count,
                COUNT(*) FILTER (WHERE status = 'REVERSED')        AS reversed_count,
                SUM(amount)                                         AS total_amount,
                COALESCE(SUM(amount) FILTER (WHERE status = 'SETTLED'), 0)
                                                                    AS settled_amount,
                COALESCE(SUM(amount) FILTER (WHERE status = 'SETTLED'), 0)
                    - COALESCE(SUM(amount) FILTER (WHERE status IN ('REJECTED','REVERSED')), 0)
                                                                    AS net_amount
            FROM transactions
            WHERE business_date = ?
            GROUP BY business_date, source_bank, destination_bank, currency
            ON CONFLICT (summary_date, source_bank, destination_bank, currency) DO UPDATE
            SET total_count    = EXCLUDED.total_count,
                settled_count  = EXCLUDED.settled_count,
                rejected_count = EXCLUDED.rejected_count,
                reversed_count = EXCLUDED.reversed_count,
                total_amount   = EXCLUDED.total_amount,
                settled_amount = EXCLUDED.settled_amount,
                net_amount     = EXCLUDED.net_amount,
                updated_at     = NOW()
            """;

    // ── hourly_transaction_summary ─────────────────────────────────────────────
    private static final String HOURLY_TXN_UPSERT = """
            INSERT INTO hourly_transaction_summary
                (summary_date, hour_of_day, source_bank, destination_bank, currency,
                 total_count, settled_count, rejected_count,
                 total_amount, settled_amount)
            SELECT
                business_date                                       AS summary_date,
                EXTRACT(HOUR FROM created_at)::SMALLINT             AS hour_of_day,
                source_bank,
                destination_bank,
                currency,
                COUNT(*)                                            AS total_count,
                COUNT(*) FILTER (WHERE status = 'SETTLED')         AS settled_count,
                COUNT(*) FILTER (WHERE status = 'REJECTED')        AS rejected_count,
                SUM(amount)                                         AS total_amount,
                COALESCE(SUM(amount) FILTER (WHERE status = 'SETTLED'), 0)
                                                                    AS settled_amount
            FROM transactions
            WHERE business_date = ?
            GROUP BY business_date, hour_of_day, source_bank, destination_bank, currency
            ON CONFLICT (summary_date, hour_of_day, source_bank, destination_bank, currency) DO UPDATE
            SET total_count    = EXCLUDED.total_count,
                settled_count  = EXCLUDED.settled_count,
                rejected_count = EXCLUDED.rejected_count,
                total_amount   = EXCLUDED.total_amount,
                settled_amount = EXCLUDED.settled_amount,
                updated_at     = NOW()
            """;

    // ── inquiry_daily_summary ──────────────────────────────────────────────────
    private static final String INQUIRY_DAILY_UPSERT = """
            INSERT INTO inquiry_daily_summary
                (summary_date, source_bank, destination_bank,
                 total_count, completed_count, failed_count, expired_count, eligible_count)
            SELECT
                business_date                                                     AS summary_date,
                source_bank,
                destination_bank,
                COUNT(*)                                                          AS total_count,
                COUNT(*) FILTER (WHERE status = 'COMPLETED')                     AS completed_count,
                COUNT(*) FILTER (WHERE status = 'FAILED')                        AS failed_count,
                COUNT(*) FILTER (WHERE status = 'EXPIRED')                       AS expired_count,
                COUNT(*) FILTER (WHERE eligible_for_transfer = TRUE)             AS eligible_count
            FROM inquiries
            WHERE business_date = ?
            GROUP BY business_date, source_bank, destination_bank
            ON CONFLICT (summary_date, source_bank, destination_bank) DO UPDATE
            SET total_count     = EXCLUDED.total_count,
                completed_count = EXCLUDED.completed_count,
                failed_count    = EXCLUDED.failed_count,
                expired_count   = EXCLUDED.expired_count,
                eligible_count  = EXCLUDED.eligible_count,
                updated_at      = NOW()
            """;

    private final JdbcTemplate jdbcTemplate;

    public AggregationService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Aggregate all three summary tables for the given date.
     *
     * @param date the business date to aggregate (usually yesterday for EOD, or today for intraday)
     */
    @Transactional
    public void aggregateForDate(LocalDate date) {
        log.info("Starting aggregation for date={}", date);

        int dailyRows  = jdbcTemplate.update(DAILY_TXN_UPSERT, date);
        int hourlyRows = jdbcTemplate.update(HOURLY_TXN_UPSERT, date);
        int inquiryRows = jdbcTemplate.update(INQUIRY_DAILY_UPSERT, date);

        log.info("Aggregation complete: date={} daily_rows={} hourly_rows={} inquiry_rows={}",
                date, dailyRows, hourlyRows, inquiryRows);
    }

    /**
     * Convenience: aggregate both yesterday (complete) and today (intraday).
     * Safe to call multiple times — all UPSERTs are idempotent.
     */
    @Transactional
    public void aggregateYesterdayAndToday() {
        LocalDate today     = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        aggregateForDate(yesterday);
        aggregateForDate(today);
    }
}
