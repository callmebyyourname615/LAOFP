package com.example.switching.outbox.deadletter.dto;

import java.time.LocalDateTime;

import com.example.switching.outbox.deadletter.entity.OutboxDeadLetterEntity;

public record DeadLetterResponse(
        Long id,
        String eventId,
        String schemaName,
        Integer schemaVersion,
        Long outboxEventId,
        String transferRef,
        String status,
        int failureCount,
        String failureType,
        String failureMessage,
        LocalDateTime firstFailedAt,
        LocalDateTime lastFailedAt,
        String replayRequestedBy,
        String replayApprovedBy,
        String replayedBy
) {
    public static DeadLetterResponse from(OutboxDeadLetterEntity entity) {
        return new DeadLetterResponse(entity.getId(), entity.getEventId(), entity.getSchemaName(),
                entity.getSchemaVersion(), entity.getOutboxEventId(), entity.getTransferRef(),
                entity.getStatus().name(), entity.getFailureCount(), entity.getFailureType(),
                entity.getFailureMessage(), entity.getFirstFailedAt(), entity.getLastFailedAt(),
                entity.getReplayRequestedBy(), entity.getReplayApprovedBy(), entity.getReplayedBy());
    }
}
