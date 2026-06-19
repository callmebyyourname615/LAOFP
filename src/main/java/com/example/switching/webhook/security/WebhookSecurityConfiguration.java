package com.example.switching.webhook.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(WebhookEndpointPolicyProperties.class)
public class WebhookSecurityConfiguration {
}
