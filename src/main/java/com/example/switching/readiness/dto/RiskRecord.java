package com.example.switching.readiness.dto;

import java.time.Instant;

public record RiskRecord(
        String riskId,
        String category,
        String severity,
        boolean waiverRequested,
        boolean waiverApproved,
        String owner,
        Instant expiresAt,
        String compensatingControl) {
}
