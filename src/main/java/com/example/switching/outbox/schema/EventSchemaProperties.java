package com.example.switching.outbox.schema;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "switching.outbox.schema")
public class EventSchemaProperties {

    private boolean allowLegacyMessages = false;

    public boolean isAllowLegacyMessages() {
        return allowLegacyMessages;
    }

    public void setAllowLegacyMessages(boolean allowLegacyMessages) {
        this.allowLegacyMessages = allowLegacyMessages;
    }
}
