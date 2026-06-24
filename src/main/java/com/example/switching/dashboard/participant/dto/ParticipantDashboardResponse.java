package com.example.switching.dashboard.participant.dto;

import java.time.Instant;
import java.util.List;

public record ParticipantDashboardResponse(Instant generatedAt, Summary summary, List<ParticipantHealth> participants) {
    public record Summary(long total, long active, long degraded, long inactive) {}
    public record ParticipantHealth(String bankCode, String name, String status, long transactionCount24h,
                                    long settled24h, long rejected24h, double successRatePercent,
                                    long activeConnectors, long expiringCredentials30d, Instant lastTransactionAt) {}
}
