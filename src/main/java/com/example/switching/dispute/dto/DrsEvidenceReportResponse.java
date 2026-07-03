package com.example.switching.dispute.dto;

import java.time.LocalDateTime;
import java.util.List;

public record DrsEvidenceReportResponse(
        LocalDateTime generatedAt,
        DrsDisputeListItemResponse dispute,
        DrsTransferSnapshotResponse transfer,
        DrsRefundSnapshotResponse refund,
        List<DrsTimelineItemResponse> timeline
) {}
