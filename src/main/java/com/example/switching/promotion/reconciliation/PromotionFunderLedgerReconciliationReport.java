package com.example.switching.promotion.reconciliation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.example.switching.consistency.ReadConsistency;

public record PromotionFunderLedgerReconciliationReport(
        Instant generatedAt,
        ReadConsistency consistency,
        String status,
        int promotionCount,
        int mismatchCount,
        BigDecimal totalBudgetConsumed,
        BigDecimal totalSettlementRecorded,
        List<PromotionLedgerItem> items) {

    public record PromotionLedgerItem(
            UUID promotionId,
            String promotionCode,
            String funderParticipantId,
            String currency,
            BigDecimal budgetCap,
            BigDecimal budgetReserved,
            BigDecimal budgetConsumed,
            BigDecimal reservedApplications,
            BigDecimal consumedApplications,
            BigDecimal settlementRecorded,
            BigDecimal settledAmount,
            BigDecimal pendingAmount,
            BigDecimal failedAmount,
            BigDecimal reversedAmount,
            BigDecimal reservationVariance,
            BigDecimal consumptionVariance,
            BigDecimal settlementCoverageVariance,
            String status) {
    }
}
