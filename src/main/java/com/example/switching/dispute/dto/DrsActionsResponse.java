package com.example.switching.dispute.dto;

import java.time.LocalDateTime;
import java.util.Map;

import com.example.switching.operations.dto.OperationActionDecisionResponse;

public record DrsActionsResponse(
        LocalDateTime generatedAt,
        Long disputeId,
        String txnRef,
        String status,
        String proposedResolution,
        String proposedBy,
        Long refundId,
        String refundStatus,
        Map<String, OperationActionDecisionResponse> actions
) {}
