package com.example.switching.outbox.schema;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.example.switching.outbox.queue.OutboxQueueMessage;

class EventSchemaValidatorTest {

    @Test
    void acceptsCurrentVersion() {
        EventSchemaProperties properties = new EventSchemaProperties();
        EventSchemaValidator validator = new EventSchemaValidator(properties);
        assertDoesNotThrow(() -> validator.validate(
                OutboxQueueMessage.versionOne(10L, "TRX-10", LocalDateTime.now())));
    }

    @Test
    void rejectsLegacyByDefault() {
        EventSchemaValidator validator = new EventSchemaValidator(new EventSchemaProperties());
        assertThrows(UnsupportedEventSchemaException.class,
                () -> validator.validate(new OutboxQueueMessage(10L, "TRX-10", LocalDateTime.now())));
    }

    @Test
    void legacyRequiresExplicitCompatibilityFlag() {
        EventSchemaProperties properties = new EventSchemaProperties();
        properties.setAllowLegacyMessages(true);
        EventSchemaValidator validator = new EventSchemaValidator(properties);
        assertDoesNotThrow(() -> validator.validate(
                new OutboxQueueMessage(10L, "TRX-10", LocalDateTime.now())));
    }
}
