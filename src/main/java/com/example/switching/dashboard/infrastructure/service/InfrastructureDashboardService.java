package com.example.switching.dashboard.infrastructure.service;

import com.example.switching.dashboard.common.DashboardQueryGuard;
import com.example.switching.dashboard.infrastructure.dto.InfrastructureDashboardResponse;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "switching.phase81.dashboards.enabled", havingValue = "true")
public class InfrastructureDashboardService {
    private final JdbcTemplate jdbc;
    private final DashboardQueryGuard queryGuard;
    private final String version;
    private final String commit;
    private final String image;
    public InfrastructureDashboardService(JdbcTemplate jdbc, DashboardQueryGuard queryGuard,
            @Value("${spring.application.version:unknown}") String version,
            @Value("${build.commit:unknown}") String commit,
            @Value("${build.image:unknown}") String image) {
        this.jdbc=jdbc; this.queryGuard=queryGuard; this.version=version; this.commit=commit; this.image=image;
    }

    @Transactional(readOnly = true)
    public InfrastructureDashboardResponse load() {
        queryGuard.apply();
        var db=jdbc.queryForObject("""
                SELECT pg_is_in_recovery() in_recovery,
                       count(*) FILTER (WHERE state='active') active_connections,
                       count(*) FILTER (WHERE state='idle') idle_connections,
                       CASE WHEN pg_is_in_recovery() THEN EXTRACT(EPOCH FROM now()-pg_last_xact_replay_timestamp()) END replica_lag_seconds
                  FROM pg_stat_activity
                """, (rs,row)->new InfrastructureDashboardResponse.Database(
                rs.getBoolean("in_recovery"),rs.getLong("active_connections"),rs.getLong("idle_connections"),
                number(rs.getObject("replica_lag_seconds"))));
        var outbox=jdbc.queryForObject("""
                SELECT count(*) FILTER (WHERE status='PENDING') pending,
                       count(*) FILTER (WHERE status='PROCESSING') processing,
                       count(*) FILTER (WHERE status='FAILED') failed,
                       (SELECT count(*) FROM dead_letter_messages WHERE reviewed_at IS NULL) dead_letters
                  FROM outbox_messages
                """,(rs,row)->new InfrastructureDashboardResponse.Outbox(
                rs.getLong("pending"),rs.getLong("processing"),rs.getLong("failed"),rs.getLong("dead_letters")));
        Runtime rt=Runtime.getRuntime();
        var jvm=new InfrastructureDashboardResponse.Jvm(rt.maxMemory(),rt.totalMemory(),rt.freeMemory(),rt.availableProcessors());
        return new InfrastructureDashboardResponse(Instant.now(),jvm,db,outbox,
                new InfrastructureDashboardResponse.Build(version,commit,image));
    }
    private static Double number(Object value) { return value == null ? null : ((Number) value).doubleValue(); }
}
