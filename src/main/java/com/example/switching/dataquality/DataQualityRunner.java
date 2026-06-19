package com.example.switching.dataquality;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class DataQualityRunner {
    private final JdbcTemplate jdbcTemplate;

    public DataQualityRunner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public String runEnabledRules() {
        String runId = "dq-" + UUID.randomUUID();
        var rules = jdbcTemplate.queryForList("SELECT rule_code, sql_check FROM data_quality_rule WHERE enabled = true");
        for (var rule : rules) {
            Long failing = jdbcTemplate.queryForObject(String.valueOf(rule.get("sql_check")), Long.class);
            jdbcTemplate.update("""
                    INSERT INTO data_quality_run(run_id, rule_code, status, failing_count, completed_at)
                    VALUES (?, ?, ?, ?, now())
                    """, runId, rule.get("rule_code"), failing != null && failing > 0 ? "FAIL" : "PASS", failing == null ? 0 : failing);
        }
        return runId;
    }
}
