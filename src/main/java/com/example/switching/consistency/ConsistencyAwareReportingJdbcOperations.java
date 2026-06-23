package com.example.switching.consistency;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class ConsistencyAwareReportingJdbcOperations {

    private static final Logger log =
            LoggerFactory.getLogger(ConsistencyAwareReportingJdbcOperations.class);

    private final JdbcTemplate primary;
    private final JdbcTemplate reporting;
    private final ReplicaFreshnessProbe freshnessProbe;

    public ConsistencyAwareReportingJdbcOperations(
            JdbcTemplate primary,
            @Qualifier("reportingJdbcTemplate") JdbcTemplate reporting,
            ReplicaFreshnessProbe freshnessProbe) {
        this.primary = primary;
        this.reporting = reporting;
        this.freshnessProbe = freshnessProbe;
    }

    public List<Map<String, Object>> queryForList(
            ReadConsistency consistency,
            String sql,
            Object... arguments) {
        return select(consistency).queryForList(sql, arguments);
    }

    JdbcTemplate select(ReadConsistency consistency) {
        ReadConsistency requested = consistency == null
                ? ReadConsistency.STRICT_PRIMARY
                : consistency;
        if (requested != ReadConsistency.EVENTUAL) {
            return primary;
        }

        ReplicaFreshness freshness = freshnessProbe.inspect();
        if (freshness.usableForEventualReads()) {
            return reporting;
        }
        log.warn("Eventual read routed to primary because replica is unsafe reason={} lag={}",
                freshness.reason(), freshness.replayLag());
        return primary;
    }
}
