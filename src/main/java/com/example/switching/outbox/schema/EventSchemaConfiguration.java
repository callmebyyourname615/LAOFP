package com.example.switching.outbox.schema;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(EventSchemaProperties.class)
public class EventSchemaConfiguration {
}
