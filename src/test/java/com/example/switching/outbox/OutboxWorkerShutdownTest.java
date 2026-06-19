package com.example.switching.outbox;

import com.example.switching.outbox.entity.OutboxEventEntity;
import com.example.switching.outbox.enums.OutboxStatus;
import com.example.switching.outbox.event.OutboxCreatedEvent;
import com.example.switching.outbox.repository.OutboxEventRepository;
import com.example.switching.outbox.service.OutboxProcessorService;
import com.example.switching.outbox.worker.OutboxDispatchWorker;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * P7 — Graceful shutdown verification.
 *
 * Verifies that after {@code @PreDestroy onShutdown()} fires, the
 * {@code OutboxDispatchWorker} stops accepting and processing new events.
 *
 * TC-SD-001  Pre-shutdown: events dispatched immediately on commit
 * TC-SD-002  Post-shutdown: onOutboxCreated skips processing entirely
 * TC-SD-003  Post-shutdown: scheduled poll does nothing
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OutboxWorkerShutdownTest {

    @Mock private OutboxEventRepository  outboxEventRepository;
    @Mock private OutboxProcessorService outboxProcessorService;

    private OutboxDispatchWorker worker;

    @BeforeEach
    void setUp() {
        // countByStatus is called lazily when gauges are queried — stub with lenient
        lenient().when(outboxEventRepository.countByStatus(any(OutboxStatus.class))).thenReturn(0L);

        worker = new OutboxDispatchWorker(
                outboxEventRepository,
                outboxProcessorService,
                new SimpleMeterRegistry()
        );

        // @Value("${...batch-size:20}") is not injected in unit tests — set it directly
        ReflectionTestUtils.setField(worker, "batchSize", 20);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TC-SD-001  Normal (pre-shutdown) dispatch — event is processed
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void beforeShutdown_onOutboxCreated_dispatchesEvent() {
        OutboxCreatedEvent event = new OutboxCreatedEvent(42L, "TRX-TEST-001");

        worker.onOutboxCreated(event);

        verify(outboxProcessorService, times(1)).processSingleEvent(42L);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TC-SD-002  Post-shutdown: onOutboxCreated MUST NOT process any event
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void afterShutdown_onOutboxCreated_skipsProcessing() {
        worker.onShutdown();   // simulate @PreDestroy call

        OutboxCreatedEvent event = new OutboxCreatedEvent(99L, "TRX-AFTER-SHUTDOWN");
        worker.onOutboxCreated(event);

        verify(outboxProcessorService, never()).processSingleEvent(anyLong());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TC-SD-003  Post-shutdown: scheduled processPendingEvents does nothing
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void afterShutdown_scheduledPoll_doesNotQueryOrProcess() {
        worker.onShutdown();   // simulate @PreDestroy call

        worker.processPendingEvents();

        // No DB query for pending events should happen after shutdown
        verify(outboxEventRepository, never())
                .findPendingBatch(any(OutboxStatus.class), any(LocalDateTime.class), any(Pageable.class));
        verify(outboxProcessorService, never()).processSingleEvent(anyLong());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TC-SD-004  Normal poll: pending events ARE fetched and processed
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void beforeShutdown_scheduledPoll_fetchesAndProcessesPending() {
        OutboxEventEntity mockEvent = mock(OutboxEventEntity.class);
        when(mockEvent.getId()).thenReturn(77L);
        when(outboxEventRepository.findPendingBatch(
                any(OutboxStatus.class), any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(List.of(mockEvent));

        worker.processPendingEvents();

        verify(outboxEventRepository, times(1))
                .findPendingBatch(any(OutboxStatus.class), any(LocalDateTime.class), any(Pageable.class));
        verify(outboxProcessorService, times(1)).processSingleEvent(77L);
    }
}
