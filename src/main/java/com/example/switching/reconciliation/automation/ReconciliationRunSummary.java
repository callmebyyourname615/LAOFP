package com.example.switching.reconciliation.automation;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ReconciliationRunSummary(
        UUID runId,
        LocalDate businessDate,
        String sourceSystem,
        String targetSystem,
        String status,
        long expectedCount,
        long actualCount,
        BigDecimal expectedAmount,
        BigDecimal actualAmount,
        long mismatchCount,
        Instant completedAt
) {}
