package com.example.switching.traffic.ratelimit;

public record RateLimitDecision(
        boolean allowed,
        long limit,
        long remaining,
        long retryAfterSeconds,
        String policyRevision,
        String identity) {
}
