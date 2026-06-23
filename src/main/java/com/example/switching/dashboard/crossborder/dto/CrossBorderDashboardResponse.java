package com.example.switching.dashboard.crossborder.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record CrossBorderDashboardResponse(
        Instant generatedAt,
        Summary summary,
        List<AdapterStatus> adapters,
        List<CorridorVolume> volumeToday,
        List<FxRate> currentRates,
        List<ReconciliationStatus> reconciliation) {

    public record Summary(long completedToday, long failedLastHour,
                          long failedLast24Hours, long unreconciledItems) {}
    public record AdapterStatus(String rail, String status, Instant lastMessageAt,
                                long failedLast24Hours, long pendingMessages) {}
    public record CorridorVolume(String network, long transactionCount,
                                 long completedCount, long failedCount) {}
    public record FxRate(String sourceCurrency, String destinationCurrency,
                         String network, BigDecimal indicativeRate, String status) {}
    public record ReconciliationStatus(String rail, String status, long count) {}
}
