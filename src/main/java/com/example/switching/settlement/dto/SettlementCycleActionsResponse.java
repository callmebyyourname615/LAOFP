package com.example.switching.settlement.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.example.switching.operations.dto.OperationActionDecisionResponse;

public record SettlementCycleActionsResponse(
        LocalDateTime generatedAt,
        String cycleRef,
        String cycleStatus,
        int itemCount,
        int instructionCount,
        Map<String, OperationActionDecisionResponse> actions,
        List<SettlementInstructionActionsResponse> instructionActions
) {}
