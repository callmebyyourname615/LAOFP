package com.example.switching.dashboard.crossborder.service;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.switching.dashboard.crossborder.dto.CrossBorderDashboardResponse;
import com.example.switching.dashboard.common.DashboardQueryGuard;
import com.example.switching.dashboard.common.DashboardAccessScope;

@Service
@ConditionalOnProperty(name = "switching.smos.enabled", havingValue = "true")
public class CrossBorderDashboardService {
    private static final List<String> EXPECTED_RAILS = List.of("PROMPTPAY", "BAKONG", "NAPAS", "UPI");
    private final JdbcTemplate jdbc;
    private final DashboardQueryGuard queryGuard;
    private final DashboardAccessScope accessScope;
    public CrossBorderDashboardService(@Qualifier("reportingJdbcTemplate") JdbcTemplate jdbc, DashboardQueryGuard queryGuard, DashboardAccessScope accessScope) {
        this.jdbc = jdbc;
        this.queryGuard = queryGuard;
        this.accessScope = accessScope;
    }

    @Transactional(readOnly = true)
    public CrossBorderDashboardResponse load() {
        accessScope.requireSchemeWideOperator();
        queryGuard.apply();
        CrossBorderDashboardResponse.Summary summary = jdbc.queryForObject("""
                SELECT
                  (SELECT count(*) FROM crossborder_transfers WHERE status = 'COMPLETED' AND initiated_at::date = current_date) AS completed_today,
                  (SELECT count(*) FROM cross_border_rail_message WHERE status = 'FAILED' AND created_at >= now() - interval '1 hour') AS failed_1h,
                  (SELECT count(*) FROM cross_border_rail_message WHERE status = 'FAILED' AND created_at >= now() - interval '24 hours') AS failed_24h,
                  (SELECT count(*) FROM cross_border_rail_reconciliation WHERE status <> 'MATCHED') AS unreconciled
                """, (rs, rowNum) -> new CrossBorderDashboardResponse.Summary(
                rs.getLong("completed_today"), rs.getLong("failed_1h"),
                rs.getLong("failed_24h"), rs.getLong("unreconciled")));

        Map<String, CrossBorderDashboardResponse.AdapterStatus> observed = new LinkedHashMap<>();
        jdbc.query("""
                SELECT rail, max(created_at) AS last_message_at,
                       count(*) FILTER (WHERE status = 'FAILED' AND created_at >= now() - interval '24 hours') AS failed_24h,
                       count(*) FILTER (WHERE status IN ('RECEIVED','VALIDATED','PENDING','SUBMITTED')) AS pending_messages,
                       (array_agg(status ORDER BY created_at DESC))[1] AS latest_status
                FROM cross_border_rail_message GROUP BY rail
                """, rs -> {
            String rail = rs.getString("rail");
            long failed = rs.getLong("failed_24h");
            long pending = rs.getLong("pending_messages");
            String latest = rs.getString("latest_status");
            String health = failed > 0 ? "DEGRADED" : pending > 0 ? "PROCESSING" : "AVAILABLE";
            if ("FAILED".equals(latest)) health = "DEGRADED";
            observed.put(rail, new CrossBorderDashboardResponse.AdapterStatus(
                    rail, health, instant(rs, "last_message_at"), failed, pending));
        });
        List<CrossBorderDashboardResponse.AdapterStatus> adapters = new ArrayList<>();
        for (String rail : EXPECTED_RAILS) {
            adapters.add(observed.getOrDefault(rail,
                    new CrossBorderDashboardResponse.AdapterStatus(rail, "UNKNOWN", null, 0, 0)));
        }

        List<CrossBorderDashboardResponse.CorridorVolume> volumes = jdbc.query("""
                SELECT target_network, count(*) AS transaction_count,
                       count(*) FILTER (WHERE status = 'COMPLETED') AS completed_count,
                       count(*) FILTER (WHERE status = 'FAILED') AS failed_count
                FROM crossborder_transfers WHERE initiated_at::date = current_date
                GROUP BY target_network ORDER BY transaction_count DESC
                LIMIT 20
                """, (rs, rowNum) -> new CrossBorderDashboardResponse.CorridorVolume(
                rs.getString("target_network"), rs.getLong("transaction_count"),
                rs.getLong("completed_count"), rs.getLong("failed_count")));

        List<CrossBorderDashboardResponse.FxRate> rates = jdbc.query("""
                SELECT source_currency, dest_currency, target_network, indicative_rate, status
                FROM fx_corridors ORDER BY source_currency, dest_currency, target_network
                LIMIT 100
                """, (rs, rowNum) -> new CrossBorderDashboardResponse.FxRate(
                rs.getString("source_currency"), rs.getString("dest_currency"),
                rs.getString("target_network"), nonNull(rs.getBigDecimal("indicative_rate")), rs.getString("status")));

        List<CrossBorderDashboardResponse.ReconciliationStatus> reconciliation = jdbc.query("""
                SELECT rail, status, count(*) AS total
                FROM cross_border_rail_reconciliation
                WHERE statement_date >= current_date - 7
                GROUP BY rail, status ORDER BY rail, status
                LIMIT 100
                """, (rs, rowNum) -> new CrossBorderDashboardResponse.ReconciliationStatus(
                rs.getString("rail"), rs.getString("status"), rs.getLong("total")));

        return new CrossBorderDashboardResponse(Instant.now(), summary, List.copyOf(adapters),
                volumes, rates, reconciliation);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        var timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }
    private static BigDecimal nonNull(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
}
