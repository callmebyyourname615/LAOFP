package com.example.switching.fraud;

import java.util.List;

public record FraudDecision(String transactionReference, String decision, int riskScore, List<String> matchedRules) {
    public boolean shouldBlock() {
        return "REJECT".equals(decision) || "HOLD".equals(decision);
    }
}
