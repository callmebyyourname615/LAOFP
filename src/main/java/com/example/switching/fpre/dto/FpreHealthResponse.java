package com.example.switching.fpre.dto;

public record FpreHealthResponse(
        long queueDepth,
        long retryableFailureCount,
        long terminalFailureCount,
        long reversalCountLast30Minutes,
        long suspendedPspCount,
        double retrySuccessRate,
        double avgResolutionMs) {
}
