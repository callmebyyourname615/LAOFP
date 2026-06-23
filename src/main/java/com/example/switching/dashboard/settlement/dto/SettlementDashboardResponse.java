package com.example.switching.dashboard.settlement.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record SettlementDashboardResponse(
        Instant generatedAt,
        Summary summary,
        List<Cycle> cyclesToday,
        List<Position> topPositions,
        List<Approval> recentApprovals) {

    public record Summary(long pendingInstructionCount, BigDecimal pendingInstructionAmount,
                          long failedInstructionsLast7Days, long openCyclesToday,
                          long lateCyclesToday) {}

    public record Cycle(String cycleRef, LocalDate settlementDate, int cycleNumber,
                        String status, Instant openedAt, Instant closedAt, Instant settledAt) {}

    public record Position(String cycleRef, String participantCode, String currency,
                           BigDecimal debitAmount, BigDecimal creditAmount,
                           BigDecimal netPosition, long transactionCount, String status) {}

    public record Approval(String requestId, String requestType, String maker,
                           String checker, Instant decidedAt, String executionReference) {}
}
