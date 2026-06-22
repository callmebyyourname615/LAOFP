package com.example.switching.rtp.service;

import java.util.Map;

import org.springframework.security.core.Authentication;

public record RtpActor(String actorId, String participantId, boolean privileged) {

    public static RtpActor from(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return new RtpActor("anonymous", null, false);
        }

        boolean privileged = authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority())
                        || "ROLE_OPS".equals(authority.getAuthority()));

        String actorId = authentication.getName();
        String participantId = actorId;
        if (authentication.getDetails() instanceof Map<?, ?> details) {
            Object bankCode = details.get("bankCode");
            if (bankCode instanceof String value && !value.isBlank()) {
                participantId = value;
            }
        }
        return new RtpActor(actorId, participantId, privileged);
    }
}
