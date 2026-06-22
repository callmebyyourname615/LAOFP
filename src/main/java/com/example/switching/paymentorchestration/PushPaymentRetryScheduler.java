package com.example.switching.paymentorchestration;

import java.util.List;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("!migration")
@ConditionalOnProperty(
        prefix = "switching.phase-ii.push-payment-orchestrator",
        name = "enabled",
        havingValue = "true")
public class PushPaymentRetryScheduler {

    private final JdbcTemplate jdbc;
    private final PushPaymentOrchestrator orchestrator;

    public PushPaymentRetryScheduler(
            JdbcTemplate jdbc,
            PushPaymentOrchestrator orchestrator) {
        this.jdbc = jdbc;
        this.orchestrator = orchestrator;
    }

    @Scheduled(fixedDelayString = "${switching.phase-ii.push-payment-orchestrator.retry-poll-ms:10000}")
    public void retryDueExecutions() {
        List<UUID> due = jdbc.queryForList("""
                SELECT id
                  FROM push_payment_execution
                 WHERE status='RETRY_SCHEDULED'
                   AND next_attempt_at<=now()
                 ORDER BY next_attempt_at
                 LIMIT 100
                """, UUID.class);
        for (UUID executionId : due) {
            try {
                orchestrator.retry(executionId);
            } catch (RuntimeException ignored) {
                // The orchestrator persists a bounded retry/failure transition.
            }
        }
    }
}
