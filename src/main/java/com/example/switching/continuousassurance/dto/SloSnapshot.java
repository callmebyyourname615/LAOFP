package com.example.switching.continuousassurance.dto;

import java.time.Instant;

public record SloSnapshot(
        double availabilityPercent,
        double paymentSuccessPercent,
        double p95LatencyMs,
        double reconciliationDelayMinutes,
        double errorBudgetRemainingPercent,
        Instant observedAt) {
}
