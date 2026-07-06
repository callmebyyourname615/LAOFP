package com.example.switching.settlement.dto;

import java.util.List;

public record SettlementTimelineResponse(
        String cycleRef,
        int count,
        List<SettlementTimelineItemResponse> items
) {}
