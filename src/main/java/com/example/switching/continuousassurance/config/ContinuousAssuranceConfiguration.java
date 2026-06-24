package com.example.switching.continuousassurance.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ContinuousAssuranceProperties.class)
@ConditionalOnProperty(prefix = "switching.continuous-assurance", name = "enabled", havingValue = "true")
public class ContinuousAssuranceConfiguration {
}
