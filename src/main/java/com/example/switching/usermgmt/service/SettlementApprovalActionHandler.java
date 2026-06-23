package com.example.switching.usermgmt.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import com.example.switching.settlement.service.SettlementInstructionService;
import com.fasterxml.jackson.databind.JsonNode;

@Component
@ConditionalOnProperty(name = "switching.smos.enabled", havingValue = "true")
public class SettlementApprovalActionHandler implements ControlledActionHandler {
    public static final String TYPE = "SETTLEMENT_INSTRUCTION_APPROVE";
    private final SettlementInstructionService settlementInstructions;
    public SettlementApprovalActionHandler(SettlementInstructionService settlementInstructions) {
        this.settlementInstructions = settlementInstructions;
    }
    @Override public boolean supports(String requestType) { return TYPE.equals(requestType); }
    @Override public String requiredPermission() { return "settlement.approve"; }
    @Override public String execute(JsonNode payload, String checkerUsername) {
        String instructionRef = requiredText(payload, "instructionRef");
        String note = payload.path("note").isTextual() ? payload.path("note").asText() : null;
        settlementInstructions.approve(instructionRef, checkerUsername, note);
        return instructionRef;
    }
    private static String requiredText(JsonNode payload, String field) {
        String value = payload.path(field).asText();
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value;
    }
}
