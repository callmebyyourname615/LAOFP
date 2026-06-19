package com.example.switching.security.breakglass.dto;

import java.time.LocalDateTime;

import com.example.switching.security.breakglass.entity.PrivilegedAccessSessionEntity;

public record PrivilegedAccessResponse(
        Long id, String sessionRef, String requestedBy, String reason, String ticketReference,
        int ttlMinutes, int maxUses, String approvedBy, LocalDateTime expiresAt,
        int useCount, String status, String tokenPrefix, String token
) {
    public static PrivilegedAccessResponse from(PrivilegedAccessSessionEntity entity) {
        return from(entity, null);
    }

    public static PrivilegedAccessResponse from(PrivilegedAccessSessionEntity entity, String rawToken) {
        return new PrivilegedAccessResponse(entity.getId(), entity.getSessionRef(), entity.getRequestedBy(),
                entity.getReason(), entity.getTicketReference(), entity.getRequestedTtlMinutes(),
                entity.getMaxUses(), entity.getApprovedBy(), entity.getExpiresAt(), entity.getUseCount(),
                entity.getStatus().name(), entity.getTokenPrefix(), rawToken);
    }
}
