package com.example.switching.outbox.event;

/**
 * Published after an outbox event is saved and the transaction commits.
 * Triggers near real-time dispatch via {@code OutboxDispatchWorker#onOutboxCreated}.
 */
public record OutboxCreatedEvent(Long outboxEventId, String transferRef) {
}
