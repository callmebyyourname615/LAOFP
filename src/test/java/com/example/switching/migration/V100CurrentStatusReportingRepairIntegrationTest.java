package com.example.switching.migration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.example.switching.AbstractIntegrationTest;

/** Verifies the V100 repair for V86 current-status counter transitions. */
class V100CurrentStatusReportingRepairIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    @Transactional
    void decrementNeverCreatesNegativeAggregateAndRebuildMatchesSources() {
        jdbc.update("DELETE FROM reporting.current_outbox_status WHERE status = 'PHASE60_MISSING'");

        // The V86 implementation attempted an INSERT(-1) here and violated the CHECK.
        jdbc.queryForObject(
                "SELECT reporting.adjust_current_status('reporting.current_outbox_status', ?, -1)",
                Object.class,
                "PHASE60_MISSING");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM reporting.current_outbox_status WHERE status = 'PHASE60_MISSING'",
                Integer.class)).isZero();

        jdbc.queryForObject(
                "SELECT reporting.adjust_current_status('reporting.current_outbox_status', ?, 1)",
                Object.class,
                "PHASE60_MISSING");
        jdbc.queryForObject(
                "SELECT reporting.adjust_current_status('reporting.current_outbox_status', ?, -2)",
                Object.class,
                "PHASE60_MISSING");
        assertThat(jdbc.queryForObject(
                "SELECT total_count FROM reporting.current_outbox_status WHERE status = 'PHASE60_MISSING'",
                Long.class)).isZero();

        jdbc.queryForObject("SELECT reporting.rebuild_current_status_reporting()", Object.class);

        assertAggregateMatchesSource(
                "reporting.current_transaction_status", "transactions");
        assertAggregateMatchesSource(
                "reporting.current_inquiry_status", "inquiries");
        assertAggregateMatchesSource(
                "reporting.current_outbox_status", "outbox_messages");
    }

    private void assertAggregateMatchesSource(String aggregateTable, String sourceTable) {
        Long mismatches = jdbc.queryForObject("""
                SELECT count(*)
                FROM (
                    SELECT COALESCE(a.status, s.status) AS status,
                           COALESCE(a.total_count, 0) AS aggregate_count,
                           COALESCE(s.source_count, 0) AS source_count
                    FROM %s a
                    FULL OUTER JOIN (
                        SELECT status, count(*) AS source_count
                        FROM %s
                        WHERE status IS NOT NULL
                        GROUP BY status
                    ) s ON s.status = a.status
                ) compared
                WHERE aggregate_count <> source_count
                """.formatted(aggregateTable, sourceTable), Long.class);
        assertThat(mismatches).isZero();
    }
}
