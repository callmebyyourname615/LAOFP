package com.example.switching.consistency;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class ReplicaFreshnessProbe {

    private static final Logger log = LoggerFactory.getLogger(ReplicaFreshnessProbe.class);

    private final JdbcTemplate reportingJdbcTemplate;
    private final Duration maximumAllowedLag;

    public ReplicaFreshnessProbe(
            @Qualifier("reportingJdbcTemplate") JdbcTemplate reportingJdbcTemplate,
            @Value("${switching.read-replica.max-lag:PT2S}") Duration maximumAllowedLag) {
        this.reportingJdbcTemplate = reportingJdbcTemplate;
        this.maximumAllowedLag = requirePositive(maximumAllowedLag);
    }

    public ReplicaFreshness inspect() {
        try {
            Map<String, Object> row = reportingJdbcTemplate.queryForMap("""
                    SELECT pg_is_in_recovery() AS is_replica,
                           COALESCE(
                               EXTRACT(EPOCH FROM (
                                   clock_timestamp() - pg_last_xact_replay_timestamp())),
                               0) AS replay_lag_seconds
                    """);
            boolean replica = Boolean.TRUE.equals(row.get("is_replica"));
            Duration lag = duration(row.get("replay_lag_seconds"));
            return new ReplicaFreshness(
                    replica,
                    true,
                    lag,
                    maximumAllowedLag,
                    Instant.now(),
                    replica ? "replica-probe-ok" : "configured read datasource is primary");
        } catch (Exception exception) {
            log.warn("Read-replica freshness probe failed; primary fallback required", exception);
            return new ReplicaFreshness(
                    false,
                    false,
                    null,
                    maximumAllowedLag,
                    Instant.now(),
                    "probe-failed:" + exception.getClass().getSimpleName());
        }
    }

    private static Duration duration(Object value) {
        if (!(value instanceof Number number)) {
            return Duration.ZERO;
        }
        double seconds = Math.max(0D, number.doubleValue());
        return Duration.ofMillis((long) Math.ceil(seconds * 1000D));
    }

    private static Duration requirePositive(Duration value) {
        if (value == null || value.isNegative() || value.isZero()) {
            throw new IllegalArgumentException("switching.read-replica.max-lag must be positive");
        }
        return value;
    }
}
