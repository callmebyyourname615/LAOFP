package com.example.switching.outbox.queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.example.switching.outbox.deadletter.service.OutboxDeadLetterService;
import com.example.switching.outbox.schema.EventSchemaValidator;
import com.example.switching.outbox.service.OutboxProcessorService;

@Component
@Profile("!migration")
@ConditionalOnProperty(prefix = "switching.outbox.queue", name = "enabled", havingValue = "true")
public class OutboxQueueConsumer {

    private static final Logger log = LoggerFactory.getLogger(OutboxQueueConsumer.class);

    private final OutboxProcessorService outboxProcessorService;
    private final EventSchemaValidator eventSchemaValidator;
    private final OutboxDeadLetterService deadLetterService;

    public OutboxQueueConsumer(OutboxProcessorService outboxProcessorService,
                               EventSchemaValidator eventSchemaValidator,
                               OutboxDeadLetterService deadLetterService) {
        this.outboxProcessorService = outboxProcessorService;
        this.eventSchemaValidator = eventSchemaValidator;
        this.deadLetterService = deadLetterService;
    }

    @KafkaListener(
            topics = "${switching.outbox.queue.topic:switching.outbox.dispatch}",
            groupId = "${switching.outbox.queue.group-id:switching-outbox-dispatcher}"
    )
    public void consume(OutboxQueueMessage message) {
        try {
            eventSchemaValidator.validate(message);
            log.debug("Outbox queue message consumed eventId={} outboxEventId={} transferRef={}",
                    message.eventId(), message.outboxEventId(), message.transferRef());
            outboxProcessorService.processSingleEvent(message.outboxEventId());
        } catch (RuntimeException failure) {
            // Commit is safe only after the failed event is durably quarantined. If quarantine fails,
            // the exception escapes and Kafka retries the original record.
            deadLetterService.quarantine(message, failure);
            log.error("Outbox event quarantined eventId={} outboxEventId={}",
                    message == null ? null : message.eventId(),
                    message == null ? null : message.outboxEventId(), failure);
        }
    }
}
