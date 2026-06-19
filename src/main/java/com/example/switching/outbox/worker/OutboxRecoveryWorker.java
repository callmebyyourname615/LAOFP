package com.example.switching.outbox.worker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.switching.outbox.service.OutboxRecoveryService;

@Component
public class OutboxRecoveryWorker {

    private static final Logger log = LoggerFactory.getLogger(OutboxRecoveryWorker.class);

    private final OutboxRecoveryService outboxRecoveryService;

    @Value("${switching.outbox.worker.stuck-timeout-minutes:2}")
    private int stuckTimeoutMinutes;

    public OutboxRecoveryWorker(OutboxRecoveryService outboxRecoveryService) {
        this.outboxRecoveryService = outboxRecoveryService;
    }

    @Scheduled(fixedDelayString = "${switching.outbox.worker.recovery-interval-ms:60000}")
    public void recoverStuckProcessingEvents() {
        int recoveredCount = outboxRecoveryService.recoverStuckProcessingEvents(stuckTimeoutMinutes);

        if (recoveredCount > 0) {
            log.warn("Recovered {} stuck PROCESSING outbox event(s)", recoveredCount);
        }
    }
}