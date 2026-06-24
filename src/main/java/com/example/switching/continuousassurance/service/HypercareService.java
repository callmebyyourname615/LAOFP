package com.example.switching.continuousassurance.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.example.switching.continuousassurance.dto.HypercareEvent;
import com.example.switching.continuousassurance.dto.HypercareSummary;
import com.example.switching.continuousassurance.model.HypercareStatus;

@Service
@ConditionalOnProperty(prefix = "switching.continuous-assurance", name = "enabled", havingValue = "true")
public class HypercareService {
    private volatile Instant startedAt;
    private volatile Instant completedAt;
    private final List<HypercareEvent> events = new CopyOnWriteArrayList<>();

    public synchronized HypercareSummary start(Instant at) {
        if (startedAt != null) throw new IllegalStateException("Hypercare already started");
        startedAt = at == null ? Instant.now() : at;
        addEvent(0, "CUTOVER", "Hypercare started", "CHANGE_MANAGER");
        return summary();
    }

    public HypercareEvent addEvent(int day, String type, String summary, String owner) {
        if (startedAt == null) throw new IllegalStateException("Hypercare not started");
        HypercareEvent event = new HypercareEvent(UUID.randomUUID().toString(), day, type, summary, owner, Instant.now());
        events.add(event);
        return event;
    }

    public synchronized HypercareSummary complete() {
        HypercareSummary current = summary();
        if (current.status() != HypercareStatus.EXIT_READY) {
            throw new IllegalStateException("Hypercare exit criteria are not complete");
        }
        completedAt = Instant.now();
        return summary();
    }

    public HypercareSummary summary() {
        if (startedAt == null) return new HypercareSummary(HypercareStatus.NOT_STARTED, null, 0, List.of(), required(), List.of());
        int day = Math.max(0, (int) Duration.between(startedAt, Instant.now()).toDays());
        List<String> completed = new ArrayList<>();
        for (HypercareEvent e : events) completed.add(e.type());
        List<String> missing = new ArrayList<>(required());
        missing.removeAll(completed);
        HypercareStatus status = completedAt != null ? HypercareStatus.COMPLETED
                : missing.isEmpty() && day >= 14 ? HypercareStatus.EXIT_READY : HypercareStatus.ACTIVE;
        if (events.stream().anyMatch(e -> "HOLD".equals(e.type()))) status = HypercareStatus.HOLD;
        return new HypercareSummary(status, startedAt, day, List.copyOf(completed), List.copyOf(missing),
                events.stream().sorted(Comparator.comparing(HypercareEvent::occurredAt)).toList());
    }

    private static List<String> required() {
        return List.of("CUTOVER", "DAY1_RECON", "DAY3_SETTLEMENT", "DAY7_WEEKLY_RECON", "DAY14_EXIT_REVIEW");
    }
}
