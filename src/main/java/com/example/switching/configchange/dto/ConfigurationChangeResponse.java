package com.example.switching.configchange.dto;

import java.time.LocalDateTime;

import com.example.switching.configchange.entity.ConfigurationChangeRequestEntity;

public record ConfigurationChangeResponse(
        Long id, String requestRef, String targetType, String targetKey,
        String previousValue, String desiredValue, String payloadSha256,
        String reason, String ticketReference, String status,
        String requestedBy, LocalDateTime requestedAt, LocalDateTime expiresAt,
        String approvedBy, String executedBy, String rejectedBy, String rejectionReason
) {
    public static ConfigurationChangeResponse from(ConfigurationChangeRequestEntity entity) {
        return new ConfigurationChangeResponse(entity.getId(), entity.getRequestRef(), entity.getTargetType().name(),
                entity.getTargetKey(), entity.getPreviousValue(), entity.getDesiredValue(), entity.getPayloadSha256(),
                entity.getReason(), entity.getTicketReference(), entity.getStatus().name(), entity.getRequestedBy(),
                entity.getRequestedAt(), entity.getExpiresAt(), entity.getApprovedBy(), entity.getExecutedBy(),
                entity.getRejectedBy(), entity.getRejectionReason());
    }
}
