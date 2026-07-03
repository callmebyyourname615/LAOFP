package com.example.switching.dispute.dto;

import java.util.List;

public record DrsTimelineResponse(
        Long disputeId,
        String txnRef,
        int count,
        List<DrsTimelineItemResponse> items
) {}
