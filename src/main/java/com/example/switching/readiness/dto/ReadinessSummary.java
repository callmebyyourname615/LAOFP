package com.example.switching.readiness.dto;

import java.time.Instant;
import java.util.List;

import com.example.switching.readiness.model.ReadinessDecision;

public record ReadinessSummary(
        ReadinessDecision decision,
        String releaseCommit,
        String imageDigest,
        int passedControls,
        int failedControls,
        int missingRequiredControls,
        int syntheticEvidenceCount,
        int validApprovals,
        int requiredApprovals,
        int openCriticalIncidents,
        List<String> blockers,
        Instant evaluatedAt) {
}
