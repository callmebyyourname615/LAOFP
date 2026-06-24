package com.example.switching.readiness.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ReadinessProperties.class)
@ConditionalOnProperty(prefix = "switching.readiness", name = "enabled", havingValue = "true")
public class ReadinessConfiguration {
}
