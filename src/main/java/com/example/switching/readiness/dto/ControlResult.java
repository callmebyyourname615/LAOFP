package com.example.switching.readiness.dto;

import java.time.Instant;
import java.util.Map;

import com.example.switching.readiness.model.ControlStatus;

public record ControlResult(
        String controlId,
        ControlStatus status,
        boolean required,
        boolean synthetic,
        String gitCommit,
        Instant observedAt,
        Map<String, Object> observed,
        Map<String, Object> threshold,
        String evidenceId) {

    public ControlResult {
        observed = observed == null ? Map.of() : Map.copyOf(observed);
        threshold = threshold == null ? Map.of() : Map.copyOf(threshold);
    }
}
