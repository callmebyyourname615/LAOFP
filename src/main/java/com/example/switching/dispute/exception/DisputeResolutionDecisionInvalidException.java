package com.example.switching.dispute.exception;

public class DisputeResolutionDecisionInvalidException extends RuntimeException {
    public DisputeResolutionDecisionInvalidException(String decision) {
        super("Invalid DRS resolution decision: " + decision);
    }
}
