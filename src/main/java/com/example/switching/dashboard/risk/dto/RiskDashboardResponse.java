package com.example.switching.dashboard.risk.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record RiskDashboardResponse(
        Instant generatedAt,
        Summary summary,
        List<SeverityCount> alertsBySeverity,
        List<SanctionsHit> sanctionsHitsPendingReview,
        List<ParticipantRisk> topRiskParticipants,
        Aging aging) {

    public record Summary(long activeAlerts, long velocityViolations24h,
                          long sanctionsHits24h, long blockedTransactions24h) {}
    public record SeverityCount(String severity, long count) {}
    public record SanctionsHit(String transactionId, BigDecimal matchScore,
                               String matchedEntity, String listType, Instant screenedAt) {}
    public record ParticipantRisk(String participantCode, long alertCount,
                                  BigDecimal totalAmount, BigDecimal averageScore) {}
    public record Aging(long lessThan1Hour, long oneTo4Hours, long fourTo24Hours, long over24Hours) {}
}
