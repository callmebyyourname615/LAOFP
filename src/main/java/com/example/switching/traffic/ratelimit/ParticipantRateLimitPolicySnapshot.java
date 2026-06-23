package com.example.switching.traffic.ratelimit;

import java.time.Instant;

public record ParticipantRateLimitPolicySnapshot(
        String revision,
        Instant loadedAt,
        ParticipantRateLimitPolicy policy) {
}
