package com.example.switching.dashboard.transaction.service;

import com.example.switching.dashboard.common.DashboardQueryGuard;
import com.example.switching.dashboard.transaction.dto.TransactionDashboardResponse;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "switching.phase81.dashboards.enabled", havingValue = "true")
public class TransactionDashboardService {
    private final JdbcTemplate jdbc;
    private final DashboardQueryGuard queryGuard;

    public TransactionDashboardService(JdbcTemplate jdbc, DashboardQueryGuard queryGuard) {
        this.jdbc = jdbc;
        this.queryGuard = queryGuard;
    }

    @Transactional(readOnly = true)
    public TransactionDashboardResponse load() {
        queryGuard.apply();
        var summary = jdbc.queryForObject("""
                SELECT count(*) total_count,
                       COALESCE(sum(amount), 0) total_amount,
                       count(*) FILTER (WHERE status = 'SETTLED') settled_count,
                       count(*) FILTER (WHERE status = 'REJECTED') rejected_count,
                       count(*) FILTER (WHERE status NOT IN ('SETTLED','REJECTED','REFUNDED')) pending_count
                  FROM transactions
                 WHERE business_date >= current_date - 1
                """, (rs, row) -> {
                    long total = rs.getLong("total_count");
                    long settled = rs.getLong("settled_count");
                    double rate = total == 0 ? 100.0 : (settled * 100.0 / total);
                    return new TransactionDashboardResponse.Summary(total, money(rs, "total_amount"), settled,
                            rs.getLong("rejected_count"), rs.getLong("pending_count"), rate);
                });
        List<TransactionDashboardResponse.StatusCount> statuses = jdbc.query("""
                SELECT status, count(*) AS count, COALESCE(sum(amount),0) AS amount
                  FROM transactions
                 WHERE business_date >= current_date - 1
                 GROUP BY status ORDER BY count(*) DESC LIMIT 20
                """, (rs, row) -> new TransactionDashboardResponse.StatusCount(
                rs.getString("status"), rs.getLong("count"), money(rs, "amount")));
        List<TransactionDashboardResponse.Failure> failures = jdbc.query("""
                SELECT COALESCE(error_code,'UNCLASSIFIED') error_code, count(*) AS count,
                       max(COALESCE(rejected_at, updated_at, created_at)) latest_at
                  FROM transactions
                 WHERE status = 'REJECTED' AND business_date >= current_date - 7
                 GROUP BY COALESCE(error_code,'UNCLASSIFIED')
                 ORDER BY count(*) DESC LIMIT 20
                """, (rs, row) -> new TransactionDashboardResponse.Failure(
                rs.getString("error_code"), rs.getLong("count"), instant(rs, "latest_at")));
        List<TransactionDashboardResponse.TrendPoint> trend = jdbc.query("""
                SELECT date_trunc('hour', created_at) bucket, count(*) AS count,
                       COALESCE(sum(amount),0) amount,
                       count(*) FILTER (WHERE status='REJECTED') rejected_count
                  FROM transactions
                 WHERE created_at >= now() - interval '24 hours'
                 GROUP BY date_trunc('hour', created_at)
                 ORDER BY bucket
                """, (rs, row) -> new TransactionDashboardResponse.TrendPoint(
                instant(rs, "bucket"), rs.getLong("count"), money(rs, "amount"), rs.getLong("rejected_count")));
        var outbox = jdbc.queryForObject("""
                SELECT count(*) FILTER (WHERE status='PENDING') pending,
                       count(*) FILTER (WHERE status='PROCESSING') processing,
                       count(*) FILTER (WHERE status='FAILED') failed,
                       (SELECT count(*) FROM dead_letter_messages WHERE reviewed_at IS NULL) dead_letters
                  FROM outbox_messages
                """, (rs, row) -> new TransactionDashboardResponse.OutboxHealth(
                rs.getLong("pending"), rs.getLong("processing"), rs.getLong("failed"), rs.getLong("dead_letters")));
        return new TransactionDashboardResponse(Instant.now(), summary, statuses, failures, trend, outbox);
    }

    private static BigDecimal money(ResultSet rs, String column) throws SQLException {
        BigDecimal value = rs.getBigDecimal(column);
        return value == null ? BigDecimal.ZERO : value;
    }
    private static Instant instant(ResultSet rs, String column) throws SQLException {
        var ts = rs.getTimestamp(column);
        return ts == null ? null : ts.toInstant();
    }
}
