package com.example.switching.readiness.dto;

import java.time.Instant;

import com.example.switching.readiness.model.IncidentSeverity;

public record IncidentRecord(
        String incidentId,
        IncidentSeverity severity,
        String summary,
        boolean open,
        Instant occurredAt) {
}
