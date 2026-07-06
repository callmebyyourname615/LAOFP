package com.example.switching.dispute.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record DrsRefundSnapshotResponse(
        Long refundId,
        Long disputeId,
        String originalTxnRef,
        String refundRef,
        BigDecimal amount,
        String status,
        LocalDateTime initiatedAt,
        LocalDateTime completedAt,
        String lastError
) {}
