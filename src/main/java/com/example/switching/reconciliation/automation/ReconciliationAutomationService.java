package com.example.switching.reconciliation.automation;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@Service
public class ReconciliationAutomationService {
    private final JdbcTemplate jdbcTemplate;

    public ReconciliationAutomationService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public ReconciliationRunSummary closeRun(LocalDate businessDate, String source, String target, String runType,
                                             long expectedCount, long actualCount,
                                             BigDecimal expectedAmount, BigDecimal actualAmount,
                                             String evidenceUri, String evidenceSha256) {
        long mismatchCount = Math.abs(expectedCount - actualCount);
        String status = mismatchCount == 0 && expectedAmount.compareTo(actualAmount) == 0 ? "MATCHED" : "EXCEPTION";
        UUID runId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO reconciliation_control_run
                    (id, business_date, source_system, target_system, run_type, status,
                     expected_count, actual_count, expected_amount, actual_amount, mismatch_count,
                     evidence_uri, evidence_sha256, completed_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())
                ON CONFLICT (business_date, source_system, target_system, run_type)
                DO UPDATE SET status = EXCLUDED.status,
                              expected_count = EXCLUDED.expected_count,
                              actual_count = EXCLUDED.actual_count,
                              expected_amount = EXCLUDED.expected_amount,
                              actual_amount = EXCLUDED.actual_amount,
                              mismatch_count = EXCLUDED.mismatch_count,
                              evidence_uri = EXCLUDED.evidence_uri,
                              evidence_sha256 = EXCLUDED.evidence_sha256,
                              completed_at = now()
                """, runId, businessDate, source, target, runType, status, expectedCount, actualCount,
                expectedAmount, actualAmount, mismatchCount, evidenceUri, evidenceSha256);
        return new ReconciliationRunSummary(runId, businessDate, source, target, status, expectedCount, actualCount,
                expectedAmount, actualAmount, mismatchCount, Instant.now());
    }

    public Map<String, Object> latestStatus(LocalDate businessDate) {
        return jdbcTemplate.queryForMap("""
                SELECT count(*) AS run_count,
                       count(*) FILTER (WHERE status = 'EXCEPTION') AS exception_count,
                       coalesce(sum(mismatch_count), 0) AS mismatch_count
                FROM reconciliation_control_run
                WHERE business_date = ?
                """, businessDate);
    }
}
