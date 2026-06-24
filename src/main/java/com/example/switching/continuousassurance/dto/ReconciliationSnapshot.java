package com.example.switching.continuousassurance.dto;

import java.time.Instant;

public record ReconciliationSnapshot(
        long transactionCount,
        long ledgerEntryCount,
        long unmatchedTransactions,
        long ledgerImbalances,
        long duplicatePostings,
        long unpublishedOutboxEvents,
        Instant observedAt) {
    public boolean financiallyClean() {
        return unmatchedTransactions == 0 && ledgerImbalances == 0 && duplicatePostings == 0;
    }
}
