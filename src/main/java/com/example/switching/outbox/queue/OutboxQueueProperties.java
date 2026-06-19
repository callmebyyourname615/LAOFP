package com.example.switching.outbox.queue;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class OutboxQueueProperties {

    private final boolean enabled;
    private final String topic;

    public OutboxQueueProperties(
            @Value("${switching.outbox.queue.enabled:false}") boolean enabled,
            @Value("${switching.outbox.queue.topic:switching.outbox.dispatch}") String topic) {
        this.enabled = enabled;
        this.topic = topic;
    }

    public boolean enabled() {
        return enabled;
    }

    public String topic() {
        return topic;
    }
}
