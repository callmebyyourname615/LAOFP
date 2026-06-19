package com.example.switching.observability;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Runtime-only registration for database-backed operational gauges.
 *
 * <p>The collector is intentionally created through a {@code @Bean} factory instead of component
 * scanning. This avoids class-based proxy construction issues and makes the activation contract
 * explicit: enabled by default for every normal runtime profile, disabled for the one-shot
 * {@code migration} process, and optionally suppressible for constrained diagnostic processes.</p>
 */
@Configuration(proxyBeanMethods = false)
@Profile("!migration")
public class OperationalMetricsConfiguration {

    @Bean
    @ConditionalOnProperty(
            prefix = "switching.observability.operational-metrics",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    OperationalMetricsCollector operationalMetricsCollector(
            JdbcTemplate jdbcTemplate,
            MeterRegistry meterRegistry) {
        return new OperationalMetricsCollector(jdbcTemplate, meterRegistry);
    }
}
