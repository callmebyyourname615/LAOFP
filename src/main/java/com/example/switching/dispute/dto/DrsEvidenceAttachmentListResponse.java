package com.example.switching.dispute.dto;

import java.util.List;

public record DrsEvidenceAttachmentListResponse(
        Long disputeId,
        int count,
        List<DrsEvidenceAttachmentResponse> items
) {}
