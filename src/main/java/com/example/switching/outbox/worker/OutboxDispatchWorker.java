package com.example.switching.outbox.worker;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import jakarta.annotation.PreDestroy;

import com.example.switching.outbox.entity.OutboxEventEntity;
import com.example.switching.outbox.enums.OutboxStatus;
import com.example.switching.outbox.event.OutboxCreatedEvent;
import com.example.switching.outbox.repository.OutboxEventRepository;
import com.example.switching.outbox.service.OutboxProcessorService;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

@Component
public class OutboxDispatchWorker {

    private static final Logger log = LoggerFactory.getLogger(OutboxDispatchWorker.class);

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxProcessorService outboxProcessorService;

    private volatile boolean shuttingDown = false;

    @Value("${switching.outbox.worker.batch-size:20}")
    private int batchSize;

    @Value("${switching.outbox.queue.enabled:false}")
    private boolean queueEnabled;

    public OutboxDispatchWorker(OutboxEventRepository outboxEventRepository,
                                OutboxProcessorService outboxProcessorService,
                                MeterRegistry meterRegistry) {
        this.outboxEventRepository = outboxEventRepository;
        this.outboxProcessorService = outboxProcessorService;
        Gauge.builder("payment.outbox.pending.count", outboxEventRepository,
                        r -> r.countByStatus(OutboxStatus.PENDING))
                .description("Number of outbox events currently waiting to be dispatched")
                .register(meterRegistry);
        Gauge.builder("payment.outbox.processing.count", outboxEventRepository,
                        r -> r.countByStatus(OutboxStatus.PROCESSING))
                .description("Number of outbox events currently being processed")
                .register(meterRegistry);
        Gauge.builder("payment.outbox.failed.count", outboxEventRepository,
                        r -> r.countByStatus(OutboxStatus.FAILED))
                .description("Number of outbox events in terminal FAILED state")
                .register(meterRegistry);
    }

    @PreDestroy
    public void onShutdown() {
        log.info("Outbox worker shutting down — no new events will be dispatched");
        shuttingDown = true;
    }

    // ── Near real-time: triggered immediately after transaction commits ────────
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOutboxCreated(OutboxCreatedEvent event) {
        if (queueEnabled) {
            log.debug("Outbox queue enabled — queue publisher will wake dispatcher outboxEventId={} transferRef={}",
                    event.outboxEventId(), event.transferRef());
            return;
        }
        if (shuttingDown) {
            log.warn("Outbox worker shutting down — skipping near-real-time dispatch outboxEventId={}", event.outboxEventId());
            return;
        }
        log.debug("Near real-time dispatch triggered: outboxEventId={} transferRef={}",
                event.outboxEventId(), event.transferRef());
        outboxProcessorService.processSingleEvent(event.outboxEventId());
    }

    // ── Safety net: polls DB every N seconds to catch anything missed ─────────
    @Scheduled(fixedDelayString = "${switching.outbox.worker.poll-interval-ms:30000}")
    public void processPendingEvents() {
        if (shuttingDown) {
            return;
        }

        List<OutboxEventEntity> events = outboxEventRepository
                .findPendingBatch(OutboxStatus.PENDING, LocalDateTime.now(), PageRequest.of(0, batchSize));

        if (events.isEmpty()) {
            return;
        }

        log.info("Outbox safety-net poll found {} pending event(s)", events.size());

        for (OutboxEventEntity event : events) {
            if (shuttingDown) {
                log.info("Outbox worker shutting down — stopping mid-batch at outboxEventId={}", event.getId());
                break;
            }
            outboxProcessorService.processSingleEvent(event.getId());
        }
    }
}
