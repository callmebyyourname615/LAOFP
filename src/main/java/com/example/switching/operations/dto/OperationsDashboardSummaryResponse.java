package com.example.switching.operations.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OperationsDashboardSummaryResponse(
        String status,
        LocalDateTime checkedAt,
        TransferSummary transfers,
        OutboxSummary outbox,
        IsoMessageSummary isoMessages,
        BankSummary banks,
        ConnectorSummary connectors,
        RoutingSummary routingRules,
        List<LatestTransactionItem> latestTransactions,
        List<OutboxAlertItem> failedOutboxEvents,
        List<OutboxAlertItem> stuckOutboxEvents,
        String message
) {
    public record TransferSummary(
            Long total,
            Long received,
            Long success,
            Long failed
    ) {
    }

    public record OutboxSummary(
            Long total,
            Long pending,
            Long processing,
            Long success,
            Long failed,
            Long stuckProcessing
    ) {
    }

    public record IsoMessageSummary(
            Long total,
            Long pacs008Outbound,
            Long pacs002Inbound,
            Long encrypted,
            Long decrypted,
            Long notValidated,
            Long valid,
            Long invalid
    ) {
    }

    public record BankSummary(
            Long total,
            Long active,
            Long inactive,
            Long maintenance
    ) {
    }

    public record ConnectorSummary(
            Long total,
            Long enabled,
            Long disabled,
            Long forceReject
    ) {
    }

    public record RoutingSummary(
            Long total,
            Long enabled,
            Long disabled
    ) {
    }

    public record LatestTransactionItem(
            Long id,
            String transferRef,
            String inquiryRef,
            String sourceBank,
            String destinationBank,
            BigDecimal amount,
            String currency,
            String status,
            String externalReference,
            LocalDateTime createdAt,
            String traceApiPath
    ) {
    }

    public record OutboxAlertItem(
            Long id,
            String transferRef,
            String messageType,
            String status,
            Integer retryCount,
            String lastError,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            Long stuckMinutes,
            String actionApiPath
    ) {
    }
}