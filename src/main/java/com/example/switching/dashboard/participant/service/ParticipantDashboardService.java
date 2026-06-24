package com.example.switching.dashboard.participant.service;

import com.example.switching.dashboard.common.DashboardQueryGuard;
import com.example.switching.dashboard.participant.dto.ParticipantDashboardResponse;
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
public class ParticipantDashboardService {
    private final JdbcTemplate jdbc;
    private final DashboardQueryGuard queryGuard;
    public ParticipantDashboardService(JdbcTemplate jdbc, DashboardQueryGuard queryGuard) {
        this.jdbc = jdbc; this.queryGuard = queryGuard;
    }

    @Transactional(readOnly = true)
    public ParticipantDashboardResponse load() {
        queryGuard.apply();
        List<ParticipantDashboardResponse.ParticipantHealth> items = jdbc.query("""
                SELECT p.bank_code, p.bank_name, p.status,
                       count(t.*) txn_count,
                       count(t.*) FILTER (WHERE t.status='SETTLED') settled_count,
                       count(t.*) FILTER (WHERE t.status='REJECTED') rejected_count,
                       max(t.created_at) last_txn,
                       (SELECT count(*) FROM connector_configs c WHERE c.bank_code=p.bank_code AND c.enabled) active_connectors,
                       (SELECT count(*) FROM connector_credentials cc JOIN connector_configs c ON c.connector_name=cc.connector_name
                         WHERE c.bank_code=p.bank_code AND cc.is_active AND cc.expires_at < now()+interval '30 days') expiring_credentials
                  FROM participants p
                  LEFT JOIN transactions t ON (t.source_bank=p.bank_code OR t.destination_bank=p.bank_code)
                    AND t.created_at >= now()-interval '24 hours'
                 GROUP BY p.bank_code,p.bank_name,p.status
                 ORDER BY p.bank_code LIMIT 500
                """, (rs, row) -> {
                    long total=rs.getLong("txn_count"), settled=rs.getLong("settled_count");
                    return new ParticipantDashboardResponse.ParticipantHealth(
                            rs.getString("bank_code"), rs.getString("bank_name"), rs.getString("status"), total,
                            settled, rs.getLong("rejected_count"), total==0?100.0:settled*100.0/total,
                            rs.getLong("active_connectors"), rs.getLong("expiring_credentials"), instant(rs,"last_txn"));
                });
        long active=items.stream().filter(i -> "ACTIVE".equalsIgnoreCase(i.status())).count();
        long inactive=items.stream().filter(i -> !"ACTIVE".equalsIgnoreCase(i.status())).count();
        long degraded=items.stream().filter(i -> i.rejected24h()>0 || i.expiringCredentials30d()>0).count();
        return new ParticipantDashboardResponse(Instant.now(),
                new ParticipantDashboardResponse.Summary(items.size(),active,degraded,inactive), items);
    }
    private static Instant instant(ResultSet rs,String col)throws SQLException { var ts=rs.getTimestamp(col); return ts==null?null:ts.toInstant(); }
}
