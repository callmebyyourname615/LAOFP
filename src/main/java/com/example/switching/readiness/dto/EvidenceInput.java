package com.example.switching.readiness.dto;

import java.time.Instant;

import com.example.switching.readiness.model.ControlStatus;

public record EvidenceInput(
        String phase,
        String controlId,
        ControlStatus status,
        String environment,
        String gitCommit,
        String imageDigest,
        Instant generatedAt,
        boolean synthetic,
        String owner,
        String artifactPath,
        String artifactSha256) {
}
