package com.example.switching.dispute.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Scheduled SLA enforcement: finds OPEN disputes whose {@code sla_deadline} has
 * passed and auto-resolves them in favour of the raising PSP (RESOLVED_REFUND,
 * {@code auto_ruled = true}).
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
     * Scan for overdue OPEN disputes and auto-resolve each one.
     * Each dispute is resolved in its own transaction (via {@link DisputeResolutionService#resolve}).
     *
     * @return number of disputes that were auto-resolved
     */
    public int checkAndEnforceSlAs() {
        List<Long> overdueIds = jdbcTemplate.queryForList(
                "SELECT dispute_id FROM disputes WHERE status = 'OPEN' AND sla_deadline < NOW()",
                Long.class);

        if (overdueIds.isEmpty()) {
            return 0;
        }

        log.info("SLA enforcement: {} overdue dispute(s) found", overdueIds.size());
        int resolved = 0;

        for (Long disputeId : overdueIds) {
            try {
                resolutionService.resolve(disputeId, null, "REFUND",
                        "Auto-resolved: SLA deadline exceeded", true);
                resolved++;
                log.info("SLA auto-resolved dispute: id={}", disputeId);
            } catch (Exception e) {
                log.error("SLA auto-resolution failed for dispute {}: {}", disputeId, e.getMessage(), e);
            }
        }
        return resolved;
    }
}
