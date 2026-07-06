package com.example.switching.dispute.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record DrsDashboardSummaryResponse(
        LocalDateTime generatedAt,
        long openCount,
        long pendingApprovalCount,
        long resolvedTodayCount,
        long escalatedCount,
        long slaBreachedCount,
        long slaDueSoonCount,
        BigDecimal totalDisputedAmount,
        long refundCompletedTodayCount,
        BigDecimal refundCompletedTodayAmount,
        long refundPendingCount,
        long refundFailedCount,
        List<DrsStatusCountResponse> statusCounts,
        List<DrsDisputeListItemResponse> recentDisputes
) {}
