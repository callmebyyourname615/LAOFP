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

    private static final String TRANSACTION_STATUS_UPSERT = """
            INSERT INTO reporting.transaction_status_daily
                (summary_date, status, total_count, calculated_at, source_through_at, source_row_count)
            SELECT business_date, status, COUNT(*), NOW(), COALESCE(MAX(updated_at), MAX(created_at), NOW()), COUNT(*)
              FROM transactions
             WHERE business_date = ?
             GROUP BY business_date, status
            ON CONFLICT (summary_date, status) DO UPDATE SET
                total_count = EXCLUDED.total_count, calculated_at = EXCLUDED.calculated_at,
                source_through_at = EXCLUDED.source_through_at, source_row_count = EXCLUDED.source_row_count
            """;

    private static final String INQUIRY_STATUS_UPSERT = """
            INSERT INTO reporting.inquiry_status_daily
                (summary_date, status, total_count, calculated_at, source_through_at, source_row_count)
            SELECT business_date, status, COUNT(*), NOW(), COALESCE(MAX(updated_at), MAX(created_at), NOW()), COUNT(*)
              FROM inquiries
             WHERE business_date = ?
             GROUP BY business_date, status
            ON CONFLICT (summary_date, status) DO UPDATE SET
                total_count = EXCLUDED.total_count, calculated_at = EXCLUDED.calculated_at,
                source_through_at = EXCLUDED.source_through_at, source_row_count = EXCLUDED.source_row_count
            """;

    private static final String OUTBOX_STATUS_UPSERT = """
            INSERT INTO reporting.outbox_status_daily
                (summary_date, status, total_count, calculated_at, source_through_at, source_row_count)
            SELECT created_at::date, status, COUNT(*), NOW(), COALESCE(MAX(updated_at), MAX(created_at), NOW()), COUNT(*)
              FROM outbox_messages
             WHERE created_at >= ?::date AND created_at < (?::date + INTERVAL '1 day')
             GROUP BY created_at::date, status
            ON CONFLICT (summary_date, status) DO UPDATE SET
                total_count = EXCLUDED.total_count, calculated_at = EXCLUDED.calculated_at,
                source_through_at = EXCLUDED.source_through_at, source_row_count = EXCLUDED.source_row_count
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
        jdbcTemplate.update("DELETE FROM reporting.transaction_status_daily WHERE summary_date = ?", date);
        jdbcTemplate.update("DELETE FROM reporting.inquiry_status_daily WHERE summary_date = ?", date);
        jdbcTemplate.update("DELETE FROM reporting.outbox_status_daily WHERE summary_date = ?", date);
        int transactionStatusRows = jdbcTemplate.update(TRANSACTION_STATUS_UPSERT, date);
        int inquiryStatusRows = jdbcTemplate.update(INQUIRY_STATUS_UPSERT, date);
        int outboxStatusRows = jdbcTemplate.update(OUTBOX_STATUS_UPSERT, date, date);
        updateRefreshState("transaction-status-daily", transactionStatusRows);
        updateRefreshState("inquiry-status-daily", inquiryStatusRows);
        updateRefreshState("outbox-status-daily", outboxStatusRows);

        log.info("Aggregation complete: date={} daily_rows={} hourly_rows={} inquiry_rows={} transaction_status_rows={} inquiry_status_rows={} outbox_status_rows={}",
                date, dailyRows, hourlyRows, inquiryRows, transactionStatusRows, inquiryStatusRows, outboxStatusRows);
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

    private void updateRefreshState(String dataset, int affectedRows) {
        jdbcTemplate.update("""
                INSERT INTO reporting.refresh_state
                    (dataset, refreshed_at, source_through_at, source_row_count, aggregation_version)
                VALUES (?, NOW(), NOW(), ?, 'v1')
                ON CONFLICT (dataset) DO UPDATE SET
                    refreshed_at = EXCLUDED.refreshed_at,
                    source_through_at = EXCLUDED.source_through_at,
                    source_row_count = EXCLUDED.source_row_count,
                    aggregation_version = EXCLUDED.aggregation_version
                """, dataset, affectedRows);
    }
}
