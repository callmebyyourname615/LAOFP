package com.example.switching.traffic.ratelimit;

import java.time.Duration;

public record ParticipantQuota(long capacity, long refillTokens, long refillPeriodSeconds) {

    public ParticipantQuota {
        if (capacity < 1 || capacity > 10_000_000) {
            throw new IllegalArgumentException("capacity must be between 1 and 10000000");
        }
        if (refillTokens < 1 || refillTokens > capacity) {
            throw new IllegalArgumentException("refillTokens must be between 1 and capacity");
        }
        if (refillPeriodSeconds < 1 || refillPeriodSeconds > 86_400) {
            throw new IllegalArgumentException("refillPeriodSeconds must be between 1 and 86400");
        }
    }

    public Duration refillPeriod() {
        return Duration.ofSeconds(refillPeriodSeconds);
    }
}
