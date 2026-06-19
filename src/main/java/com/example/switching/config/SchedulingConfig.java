package com.example.switching.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@Profile("!migration")
@EnableScheduling
public class SchedulingConfig {
}