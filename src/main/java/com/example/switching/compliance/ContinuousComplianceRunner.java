package com.example.switching.compliance;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class ContinuousComplianceRunner {
    private final JdbcTemplate jdbcTemplate;

    public ContinuousComplianceRunner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public String runControls() {
        String runId = "compliance-" + UUID.randomUUID();
        var controls = jdbcTemplate.queryForList("SELECT control_code, evidence_query FROM compliance_control_definition WHERE enabled = true");
        for (var control : controls) {
            Long failures = jdbcTemplate.queryForObject(String.valueOf(control.get("evidence_query")), Long.class);
            jdbcTemplate.update("""
                    INSERT INTO compliance_control_run(run_id, control_code, result, completed_at)
                    VALUES (?, ?, ?, now())
                    """, runId, control.get("control_code"), failures != null && failures > 0 ? "FAIL" : "PASS");
        }
        return runId;
    }
}
