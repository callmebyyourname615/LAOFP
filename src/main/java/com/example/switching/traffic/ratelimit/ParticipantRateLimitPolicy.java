package com.example.switching.traffic.ratelimit;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public record ParticipantRateLimitPolicy(
        String version,
        ParticipantQuota defaultQuota,
        Map<String, ParticipantQuota> participants) {

    public ParticipantRateLimitPolicy {
        if (version == null || !version.matches("[A-Za-z0-9._-]{1,64}")) {
            throw new IllegalArgumentException("policy version is required and must be safe");
        }
        if (defaultQuota == null) {
            throw new IllegalArgumentException("defaultQuota is required");
        }
        Map<String, ParticipantQuota> normalized = new LinkedHashMap<>();
        if (participants != null) {
            participants.forEach((participant, quota) -> {
                if (participant == null || !participant.matches("[A-Za-z0-9_-]{2,64}")) {
                    throw new IllegalArgumentException("invalid participant quota key");
                }
                if (quota == null) {
                    throw new IllegalArgumentException("participant quota must not be null");
                }
                normalized.put(participant.toUpperCase(Locale.ROOT), quota);
            });
        }
        participants = Map.copyOf(normalized);
    }

    public ParticipantQuota quotaForIdentity(String identity) {
        String participant = participantFromIdentity(identity);
        return participant == null ? defaultQuota : participants.getOrDefault(participant, defaultQuota);
    }

    private static String participantFromIdentity(String identity) {
        if (identity == null || !identity.startsWith("participant:")) {
            return null;
        }
        String value = identity.substring("participant:".length()).trim();
        return value.isEmpty() ? null : value.toUpperCase(Locale.ROOT);
    }

    public static ParticipantRateLimitPolicy fallback(int requestsPerMinute) {
        int safe = Math.max(1, requestsPerMinute);
        return new ParticipantRateLimitPolicy(
                "fallback-" + safe,
                new ParticipantQuota(safe, safe, 60),
                Map.of());
    }
}
