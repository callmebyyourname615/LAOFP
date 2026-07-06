package com.example.switching.settlement.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SettlementCycleSummaryResponse(
        int itemCount,
        int transferCount,
        int participantCount,
        int instructionCount,
        int pendingApprovalCount,
        int approvedInstructionCount,
        int sentRtgsCount,
        int confirmedInstructionCount,
        int failedInstructionCount,
        BigDecimal totalDebit,
        BigDecimal totalCredit,
        BigDecimal netImbalance,
        String currency,
        LocalDateTime latestEventAt
) {}
