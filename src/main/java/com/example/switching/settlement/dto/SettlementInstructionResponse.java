package com.example.switching.settlement.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SettlementInstructionResponse(
        Long id,
        String instructionRef,
        String cycleRef,
        String sourceType,
        String transferRef,
        String debtorPspId,
        String creditorPspId,
        String currency,
        BigDecimal netAmount,
        String status,
        String approvalNote,
        String approvedBy,
        LocalDateTime approvedAt,
        String rejectedBy,
        LocalDateTime rejectedAt,
        String rejectionReason,
        String rtgsMsgId,
        String lastError,
        LocalDateTime sentAt,
        LocalDateTime confirmedAt,
        LocalDateTime createdAt
) {}
