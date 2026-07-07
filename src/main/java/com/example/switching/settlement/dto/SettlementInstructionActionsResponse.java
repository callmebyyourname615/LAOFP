package com.example.switching.settlement.dto;

import java.util.Map;

import com.example.switching.operations.dto.OperationActionDecisionResponse;

public record SettlementInstructionActionsResponse(
        String instructionRef,
        String status,
        Map<String, OperationActionDecisionResponse> actions
) {}
