package com.example.switching.outbox.queue;

import java.time.LocalDateTime;
import java.util.UUID;

public record OutboxQueueMessage(
        String schemaName,
        Integer schemaVersion,
        String eventId,
        Long outboxEventId,
        String transferRef,
        LocalDateTime queuedAt,
        String traceId
) {
    public static final String SCHEMA_NAME = "switching.outbox.dispatch";
    public static final int SCHEMA_VERSION = 1;

    /** Backward-compatible constructor for pre-tracing producers and tests. */
    public OutboxQueueMessage(Long outboxEventId, String transferRef, LocalDateTime queuedAt) {
        this(null, null, null, outboxEventId, transferRef, queuedAt, null);
    }

    /** Backward-compatible constructor for already versioned schema-v1 callers. */
    public OutboxQueueMessage(String schemaName, Integer schemaVersion, String eventId,
            Long outboxEventId, String transferRef, LocalDateTime queuedAt) {
        this(schemaName, schemaVersion, eventId, outboxEventId, transferRef, queuedAt, null);
    }

    public static OutboxQueueMessage versionOne(Long outboxEventId,
                                                 String transferRef,
                                                 LocalDateTime queuedAt) {
        return versionOne(outboxEventId, transferRef, queuedAt, null);
    }

    public static OutboxQueueMessage versionOne(Long outboxEventId,
                                                 String transferRef,
                                                 LocalDateTime queuedAt,
                                                 String traceId) {
        return new OutboxQueueMessage(
                SCHEMA_NAME,
                SCHEMA_VERSION,
                UUID.randomUUID().toString(),
                outboxEventId,
                transferRef,
                queuedAt,
                traceId);
    }
}
