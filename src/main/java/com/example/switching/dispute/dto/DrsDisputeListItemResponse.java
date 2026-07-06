package com.example.switching.dispute.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record DrsDisputeListItemResponse(
        Long disputeId,
        String txnRef,
        String raisingPspId,
        String respondingPspId,
        String disputeType,
        String status,
        LocalDateTime raisedAt,
        LocalDateTime slaDeadline,
        LocalDateTime resolvedAt,
        String proposedResolution,
        String proposedBy,
        LocalDateTime proposedAt,
        String checkedBy,
        LocalDateTime checkedAt,
        String resolutionNote,
        BigDecimal amount,
        String currency,
        String sourceBank,
        String destinationBank,
        String transferStatus,
        String confirmationStatus,
        String settlementConfidence,
        LocalDateTime settledAt,
        LocalDateTime updatedAt,
        Long refundId,
        String refundRef,
        BigDecimal refundAmount,
        String refundStatus,
        LocalDateTime refundInitiatedAt,
        LocalDateTime refundCompletedAt,
        String refundLastError
) {}
