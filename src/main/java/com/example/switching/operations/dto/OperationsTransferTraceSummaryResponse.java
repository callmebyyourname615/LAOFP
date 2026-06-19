package com.example.switching.operations.dto;

public record OperationsTransferTraceSummaryResponse(
        Integer outboxEventCount,
        Integer isoMessageCount,
        Integer auditEventCount,
        Integer timelineEventCount,
        Boolean hasInquiry,
        Boolean hasOutboxFailure,
        Boolean hasIsoFailure,
        Boolean transferSuccessful
) {
}
