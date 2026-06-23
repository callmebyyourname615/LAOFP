package com.example.switching.dashboard.risk.service;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.switching.dashboard.risk.dto.RiskDashboardResponse;
import com.example.switching.dashboard.common.DashboardQueryGuard;
import com.example.switching.dashboard.common.DashboardAccessScope;

@Service
@ConditionalOnProperty(name = "switching.smos.enabled", havingValue = "true")
public class RiskDashboardService {
    private final JdbcTemplate jdbc;
    private final DashboardQueryGuard queryGuard;
    private final DashboardAccessScope accessScope;
    public RiskDashboardService(@Qualifier("reportingJdbcTemplate") JdbcTemplate jdbc, DashboardQueryGuard queryGuard, DashboardAccessScope accessScope) {
        this.jdbc = jdbc;
        this.queryGuard = queryGuard;
        this.accessScope = accessScope;
    }

    @Transactional(readOnly = true)
    public RiskDashboardResponse load() {
        accessScope.requireSchemeWideOperator();
        queryGuard.apply();
        RiskDashboardResponse.Summary summary = jdbc.queryForObject("""
                SELECT
                  (SELECT count(*) FROM fraud_scores WHERE action_taken IN ('FLAG','BLOCK')
                     AND scored_at >= now() - interval '24 hours') AS active_alerts,
                  (SELECT count(*) FROM fraud_velocity_decision WHERE decision IN ('REVIEW','REJECT','HOLD')
                     AND created_at >= now() - interval '24 hours') AS velocity_violations,
                  (SELECT count(*) FROM sanctions_screening_results WHERE outcome IN ('BLOCKED','MANUAL_REVIEW')
                     AND screened_at >= now() - interval '24 hours') AS sanctions_hits,
                  (SELECT count(*) FROM fraud_scores WHERE action_taken = 'BLOCK'
                     AND scored_at >= now() - interval '24 hours') AS blocked_transactions
                """, (rs, rowNum) -> new RiskDashboardResponse.Summary(
                rs.getLong("active_alerts"), rs.getLong("velocity_violations"),
                rs.getLong("sanctions_hits"), rs.getLong("blocked_transactions")));

        List<RiskDashboardResponse.SeverityCount> severities = jdbc.query("""
                SELECT risk_tier, count(*) AS total
                FROM fraud_scores
                WHERE action_taken IN ('FLAG','BLOCK') AND scored_at >= now() - interval '24 hours'
                GROUP BY risk_tier
                ORDER BY CASE risk_tier WHEN 'CRITICAL' THEN 1 WHEN 'HIGH' THEN 2 WHEN 'MEDIUM' THEN 3 ELSE 4 END
                LIMIT 20
                """, (rs, rowNum) -> new RiskDashboardResponse.SeverityCount(
                rs.getString("risk_tier"), rs.getLong("total")));

        List<RiskDashboardResponse.SanctionsHit> sanctions = jdbc.query("""
                SELECT txn_id, match_score, match_entity, list_type, screened_at
                FROM sanctions_screening_results
                WHERE outcome = 'MANUAL_REVIEW'
                ORDER BY screened_at ASC
                LIMIT 100
                """, (rs, rowNum) -> new RiskDashboardResponse.SanctionsHit(
                rs.getString("txn_id"), nonNull(rs.getBigDecimal("match_score")),
                rs.getString("match_entity"), rs.getString("list_type"), instant(rs, "screened_at")));

        List<RiskDashboardResponse.ParticipantRisk> participants = jdbc.query("""
                SELECT COALESCE(sending_psp_id, 'UNKNOWN') AS participant_code,
                       count(*) AS alert_count,
                       COALESCE(sum(amount), 0) AS total_amount,
                       COALESCE(avg(score), 0) AS average_score
                FROM fraud_scores
                WHERE action_taken IN ('FLAG','BLOCK') AND scored_at >= now() - interval '24 hours'
                GROUP BY COALESCE(sending_psp_id, 'UNKNOWN')
                ORDER BY alert_count DESC, total_amount DESC
                LIMIT 10
                """, (rs, rowNum) -> new RiskDashboardResponse.ParticipantRisk(
                rs.getString("participant_code"), rs.getLong("alert_count"),
                nonNull(rs.getBigDecimal("total_amount")), nonNull(rs.getBigDecimal("average_score"))));

        RiskDashboardResponse.Aging aging = jdbc.queryForObject("""
                SELECT
                  count(*) FILTER (WHERE scored_at >= now() - interval '1 hour') AS lt_1h,
                  count(*) FILTER (WHERE scored_at < now() - interval '1 hour' AND scored_at >= now() - interval '4 hours') AS h1_4,
                  count(*) FILTER (WHERE scored_at < now() - interval '4 hours' AND scored_at >= now() - interval '24 hours') AS h4_24,
                  count(*) FILTER (WHERE scored_at < now() - interval '24 hours') AS gt_24h
                FROM fraud_scores WHERE action_taken IN ('FLAG','BLOCK')
                """, (rs, rowNum) -> new RiskDashboardResponse.Aging(
                rs.getLong("lt_1h"), rs.getLong("h1_4"), rs.getLong("h4_24"), rs.getLong("gt_24h")));

        return new RiskDashboardResponse(Instant.now(), summary, severities, sanctions, participants, aging);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        var timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }
    private static BigDecimal nonNull(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
}
