package com.example.switching.bauactivation;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(BauActivationProperties.class)
@ConditionalOnProperty(name="switching.phase81.bau.enabled",havingValue="true")
public class BauActivationConfiguration {}
