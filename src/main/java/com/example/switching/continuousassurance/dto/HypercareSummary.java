package com.example.switching.continuousassurance.dto;

import java.time.Instant;
import java.util.List;

import com.example.switching.continuousassurance.model.HypercareStatus;

public record HypercareSummary(HypercareStatus status, Instant startedAt, int currentDay,
        List<String> completedMilestones, List<String> missingMilestones, List<HypercareEvent> events) {
}
