package com.example.switching.dispute.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Scheduled SLA enforcement: finds OPEN disputes whose {@code sla_deadline} has
 * passed and applies the safest automatic outcome for the dispute state.
 *
 * <p>The method {@link #checkAndEnforceSlAs()} is public so tests can invoke
 * it directly without waiting for the scheduler.
 */
@Profile("!migration")
@Service
public class DisputeSlaEnforcementService {

    private static final Logger log = LoggerFactory.getLogger(DisputeSlaEnforcementService.class);

    private final JdbcTemplate              jdbcTemplate;
    private final DisputeResolutionService  resolutionService;

    public DisputeSlaEnforcementService(JdbcTemplate jdbcTemplate,
                                        DisputeResolutionService resolutionService) {
        this.jdbcTemplate      = jdbcTemplate;
        this.resolutionService = resolutionService;
    }

    /**
     * Scheduled every {@code switching.dispute.sla-check-interval-ms} (default 10 min).
     * Delegates to {@link #checkAndEnforceSlAs()} so tests can call it directly.
     */
    @Scheduled(fixedDelayString = "${switching.dispute.sla-check-interval-ms:600000}")
    public void scheduledEnforce() {
        checkAndEnforceSlAs();
    }

    /**
     * Scan for overdue OPEN disputes and apply SLA handling to each one.
     * Each dispute is resolved in its own transaction (via {@link DisputeResolutionService#resolve}).
     *
     * @return number of disputes that were handled
     */
    public int checkAndEnforceSlAs() {
        List<Map<String, Object>> overdueDisputes = jdbcTemplate.queryForList("""
                SELECT dispute_id, dispute_type, txn_ref
                  FROM disputes
                 WHERE status = 'OPEN'
                   AND sla_deadline < NOW()
                """);

        if (overdueDisputes.isEmpty()) {
            return 0;
        }

        log.info("SLA enforcement: {} overdue dispute(s) found", overdueDisputes.size());
        int handled = 0;

        for (Map<String, Object> row : overdueDisputes) {
            Long disputeId = ((Number) row.get("dispute_id")).longValue();
            try {
                String disputeType = string(row.get("dispute_type"));
                String txnRef = string(row.get("txn_ref"));

                if (canAutoRefund(disputeType, txnRef)) {
                    resolutionService.resolve(disputeId, null, "REFUND",
                            "Auto-resolved: SLA deadline exceeded", true);
                    handled++;
                    log.info("SLA auto-resolved dispute: id={}", disputeId);
                    continue;
                }

                escalateForManualReview(disputeId);
                handled++;
                log.warn("SLA escalated dispute for manual review: id={} type={} txnRef={}",
                        disputeId, disputeType, txnRef);
            } catch (Exception e) {
                log.error("SLA auto-resolution failed for dispute {}: {}", disputeId, e.getMessage(), e);
            }
        }
        return handled;
    }

    private boolean canAutoRefund(String disputeType, String txnRef) {
        if ("POST_SETTLEMENT_DESTINATION_DISPUTE".equals(disputeType)) {
            return true;
        }
        Integer settledCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                  FROM transactions
                 WHERE transaction_ref = ?
                   AND status = 'SETTLED'
                """, Integer.class, txnRef);
        return settledCount != null && settledCount > 0;
    }

    private void escalateForManualReview(Long disputeId) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("""
                UPDATE disputes
                   SET status = 'ESCALATED',
                       auto_ruled = true,
                       resolution_note = ?,
                       updated_at = ?
                 WHERE dispute_id = ?
                   AND status = 'OPEN'
                """,
                "SLA deadline exceeded; dispute requires manual review because the original transfer is not settled",
                now,
                disputeId);
    }

    private String string(Object value) {
        return value != null ? value.toString() : "";
    }
}
