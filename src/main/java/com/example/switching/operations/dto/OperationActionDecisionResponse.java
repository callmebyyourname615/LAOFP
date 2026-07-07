package com.example.switching.operations.dto;

public record OperationActionDecisionResponse(
        boolean allowed,
        String reason
) {
    public static OperationActionDecisionResponse allowed(String reason) {
        return new OperationActionDecisionResponse(true, reason);
    }

    public static OperationActionDecisionResponse denied(String reason) {
        return new OperationActionDecisionResponse(false, reason);
    }
}
