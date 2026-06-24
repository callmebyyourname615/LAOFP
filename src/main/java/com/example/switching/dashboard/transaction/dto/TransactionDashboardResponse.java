package com.example.switching.dashboard.transaction.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record TransactionDashboardResponse(
        Instant generatedAt,
        Summary summary,
        List<StatusCount> statuses,
        List<Failure> topFailures,
        List<TrendPoint> hourlyTrend,
        OutboxHealth outbox) {

    public record Summary(long totalCount, BigDecimal totalAmount, long settledCount,
                          long rejectedCount, long pendingCount, double successRatePercent) {}
    public record StatusCount(String status, long count, BigDecimal amount) {}
    public record Failure(String errorCode, long count, Instant latestAt) {}
    public record TrendPoint(Instant bucket, long count, BigDecimal amount, long rejectedCount) {}
    public record OutboxHealth(long pending, long processing, long failed, long deadLetters) {}
}
