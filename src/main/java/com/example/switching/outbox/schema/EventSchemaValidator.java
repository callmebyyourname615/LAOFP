package com.example.switching.outbox.schema;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.example.switching.outbox.queue.OutboxQueueMessage;

@Component
public class EventSchemaValidator {

    private final EventSchemaProperties properties;

    public EventSchemaValidator(EventSchemaProperties properties) {
        this.properties = properties;
    }

    public void validate(OutboxQueueMessage message) {
        if (message == null) {
            throw new UnsupportedEventSchemaException("message is null");
        }
        boolean legacy = !StringUtils.hasText(message.schemaName()) && message.schemaVersion() == null;
        if (legacy && properties.isAllowLegacyMessages()) {
            return;
        }
        if (!OutboxQueueMessage.SCHEMA_NAME.equals(message.schemaName())) {
            throw new UnsupportedEventSchemaException("unsupported schema name: " + message.schemaName());
        }
        if (message.schemaVersion() == null || message.schemaVersion() != OutboxQueueMessage.SCHEMA_VERSION) {
            throw new UnsupportedEventSchemaException("unsupported schema version: " + message.schemaVersion());
        }
        if (!StringUtils.hasText(message.eventId())) {
            throw new UnsupportedEventSchemaException("eventId is required");
        }
        try {
            UUID.fromString(message.eventId());
        } catch (IllegalArgumentException ex) {
            throw new UnsupportedEventSchemaException("eventId must be a UUID");
        }
        if (message.outboxEventId() == null || message.outboxEventId() <= 0) {
            throw new UnsupportedEventSchemaException("outboxEventId must be positive");
        }
        if (message.queuedAt() == null) {
            throw new UnsupportedEventSchemaException("queuedAt is required");
        }
    }
}
