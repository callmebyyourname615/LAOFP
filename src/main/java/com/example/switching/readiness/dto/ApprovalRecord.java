package com.example.switching.readiness.dto;

import java.time.Instant;

import com.example.switching.readiness.model.ApprovalStatus;

public record ApprovalRecord(
        String role,
        String approver,
        ApprovalStatus status,
        String gitCommit,
        Instant approvedAt,
        Instant expiresAt,
        String evidenceTailHash) {
}
