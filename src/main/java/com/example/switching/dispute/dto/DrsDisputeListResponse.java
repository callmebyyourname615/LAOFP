package com.example.switching.dispute.dto;

import java.util.List;

public record DrsDisputeListResponse(
        int count,
        int limit,
        String status,
        String bankCode,
        String disputeType,
        String dateFrom,
        String dateTo,
        List<DrsDisputeListItemResponse> items
) {}
