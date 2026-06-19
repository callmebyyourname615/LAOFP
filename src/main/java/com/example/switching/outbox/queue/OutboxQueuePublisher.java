package com.example.switching.outbox.queue;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.example.switching.outbox.event.OutboxCreatedEvent;

@Component
@ConditionalOnProperty(prefix = "switching.outbox.queue", name = "enabled", havingValue = "true")
public class OutboxQueuePublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxQueuePublisher.class);

    private final KafkaTemplate<String, OutboxQueueMessage> kafkaTemplate;
    private final OutboxQueueProperties properties;

    public OutboxQueuePublisher(KafkaTemplate<String, OutboxQueueMessage> kafkaTemplate,
                                OutboxQueueProperties properties) {
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishOutboxCreated(OutboxCreatedEvent event) {
        OutboxQueueMessage message = OutboxQueueMessage.versionOne(
                event.outboxEventId(),
                event.transferRef(),
                LocalDateTime.now());

        kafkaTemplate.send(properties.topic(), String.valueOf(event.outboxEventId()), message)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish outbox queue message outboxEventId={} transferRef={}",
                                event.outboxEventId(), event.transferRef(), ex);
                        return;
                    }
                    log.debug("Published outbox queue message topic={} outboxEventId={} transferRef={}",
                            properties.topic(), event.outboxEventId(), event.transferRef());
                });
    }
}
