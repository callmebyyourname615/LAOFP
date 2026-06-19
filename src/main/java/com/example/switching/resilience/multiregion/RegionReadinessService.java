package com.example.switching.resilience.multiregion;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class RegionReadinessService {
    private final JdbcTemplate jdbcTemplate;

    public RegionReadinessService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void recordProbe(String regionCode, String probeType, String status, int latencyMs, int replicationLagSeconds) {
        jdbcTemplate.update("""
                INSERT INTO region_readiness_probe(region_code, probe_type, status, latency_ms, replication_lag_seconds)
                VALUES (?, ?, ?, ?, ?)
                """, regionCode, probeType, status, latencyMs, replicationLagSeconds);
    }

    public Map<String, Object> latestBlockers(String regionCode) {
        return jdbcTemplate.queryForMap("""
                SELECT ? AS region_code,
                       count(*) FILTER (WHERE status <> 'PASS') AS blocker_count,
                       max(observed_at) AS last_probe_at
                FROM region_readiness_probe
                WHERE region_code = ? AND observed_at > now() - interval '30 minutes'
                """, regionCode, regionCode);
    }
}
