package com.example.switching.dashboard.settlement.service;

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
import com.example.switching.dashboard.settlement.dto.SettlementDashboardResponse;
import com.example.switching.dashboard.common.DashboardQueryGuard;
import com.example.switching.dashboard.common.DashboardAccessScope;

@Service
@ConditionalOnProperty(name = "switching.smos.enabled", havingValue = "true")
public class SettlementDashboardService {
    private final JdbcTemplate jdbc;
    private final DashboardQueryGuard queryGuard;
    private final DashboardAccessScope accessScope;

    public SettlementDashboardService(@Qualifier("reportingJdbcTemplate") JdbcTemplate jdbc, DashboardQueryGuard queryGuard, DashboardAccessScope accessScope) {
        this.jdbc = jdbc;
        this.queryGuard = queryGuard;
        this.accessScope = accessScope;
    }

    @Transactional(readOnly = true)
    public SettlementDashboardResponse load() {
        accessScope.requireSchemeWideOperator();
        queryGuard.apply();
        SettlementDashboardResponse.Summary summary = jdbc.queryForObject("""
                SELECT
                  count(*) FILTER (WHERE status = 'PENDING_APPROVAL') AS pending_count,
                  COALESCE(sum(net_amount) FILTER (WHERE status = 'PENDING_APPROVAL'), 0) AS pending_amount,
                  count(*) FILTER (WHERE status = 'FAILED' AND updated_at >= now() - interval '7 days') AS failed_7d,
                  (SELECT count(*) FROM settlement_cycles WHERE settlement_date = current_date AND status = 'OPEN') AS open_cycles,
                  (SELECT count(*) FROM settlement_cycles WHERE settlement_date = current_date AND status = 'OPEN'
                     AND opened_at < now() - interval '4 hours') AS late_cycles
                FROM settlement_instructions
                """, (rs, rowNum) -> new SettlementDashboardResponse.Summary(
                rs.getLong("pending_count"), rs.getBigDecimal("pending_amount"),
                rs.getLong("failed_7d"), rs.getLong("open_cycles"), rs.getLong("late_cycles")));

        List<SettlementDashboardResponse.Cycle> cycles = jdbc.query("""
                SELECT cycle_ref, settlement_date, cycle_number, status, opened_at, closed_at, settled_at
                FROM settlement_cycles WHERE settlement_date = current_date
                ORDER BY cycle_number
                LIMIT 20
                """, (rs, rowNum) -> new SettlementDashboardResponse.Cycle(
                rs.getString("cycle_ref"), rs.getObject("settlement_date", java.time.LocalDate.class),
                rs.getInt("cycle_number"), rs.getString("status"), instant(rs, "opened_at"),
                instant(rs, "closed_at"), instant(rs, "settled_at")));

        List<SettlementDashboardResponse.Position> positions = jdbc.query("""
                SELECT c.cycle_ref, p.bank_code, p.currency, p.debit_amount, p.credit_amount,
                       p.net_position, p.transaction_count, p.status
                FROM settlement_positions p
                JOIN settlement_cycles c ON c.id = p.cycle_id
                WHERE c.settlement_date = current_date
                ORDER BY abs(p.net_position) DESC, p.bank_code
                LIMIT 10
                """, (rs, rowNum) -> new SettlementDashboardResponse.Position(
                rs.getString("cycle_ref"), rs.getString("bank_code"), rs.getString("currency"),
                nonNull(rs.getBigDecimal("debit_amount")), nonNull(rs.getBigDecimal("credit_amount")),
                nonNull(rs.getBigDecimal("net_position")), rs.getLong("transaction_count"), rs.getString("status")));

        List<SettlementDashboardResponse.Approval> approvals = jdbc.query("""
                SELECT r.id::text, r.request_type, m.username AS maker, c.username AS checker,
                       r.decided_at, r.execution_reference
                FROM smos_maker_checker_requests r
                JOIN smos_users m ON m.id = r.maker_id
                LEFT JOIN smos_users c ON c.id = r.checker_id
                WHERE r.status = 'APPROVED' AND r.request_type LIKE 'SETTLEMENT%'
                ORDER BY r.decided_at DESC NULLS LAST
                LIMIT 20
                """, (rs, rowNum) -> new SettlementDashboardResponse.Approval(
                rs.getString(1), rs.getString("request_type"), rs.getString("maker"),
                rs.getString("checker"), instant(rs, "decided_at"), rs.getString("execution_reference")));

        return new SettlementDashboardResponse(Instant.now(), summary, cycles, positions, approvals);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        var timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }
    private static BigDecimal nonNull(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
}
