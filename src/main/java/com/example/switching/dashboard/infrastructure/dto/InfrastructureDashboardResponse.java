package com.example.switching.dashboard.infrastructure.dto;

import java.time.Instant;

public record InfrastructureDashboardResponse(Instant generatedAt, Jvm jvm, Database database,
                                               Outbox outbox, Build build) {
    public record Jvm(long maxMemoryBytes, long totalMemoryBytes, long freeMemoryBytes, int processors) {}
    public record Database(boolean inRecovery, long activeConnections, long idleConnections, Double replicaLagSeconds) {}
    public record Outbox(long pending, long processing, long failed, long deadLetters) {}
    public record Build(String version, String commit, String image) {}
}
