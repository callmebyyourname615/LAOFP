package com.example.switching.consistency;

import java.time.Duration;
import java.time.Instant;

public record ReplicaFreshness(
        boolean replica,
        boolean reachable,
        Duration replayLag,
        Duration maximumAllowedLag,
        Instant observedAt,
        String reason) {

    public boolean usableForEventualReads() {
        return reachable
                && replica
                && replayLag != null
                && !replayLag.isNegative()
                && replayLag.compareTo(maximumAllowedLag) <= 0;
    }
}
