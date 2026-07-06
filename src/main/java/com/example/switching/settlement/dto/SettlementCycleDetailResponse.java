package com.example.switching.settlement.dto;

import java.time.LocalDateTime;
import java.util.List;

public record SettlementCycleDetailResponse(
        LocalDateTime generatedAt,
        SettlementCycleResponse cycle,
        SettlementCycleSummaryResponse summary,
        List<SettlementPositionResponse> positions,
        List<SettlementInstructionResponse> instructions,
        List<SettlementTransferItemResponse> transfers,
        List<SettlementReportArtifactResponse> reports,
        SettlementTimelineResponse timelinePreview
) {}
