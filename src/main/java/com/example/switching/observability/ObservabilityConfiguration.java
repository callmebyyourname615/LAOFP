package com.example.switching.observability;

import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.config.MeterFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!migration")
public class ObservabilityConfiguration {

    @Bean
    MeterFilter commonMetricTags(
            @Value("${spring.application.name:switching-api}") String application,
            @Value("${switching.observability.environment:unknown}") String environment) {
        return MeterFilter.commonTags(Tags.of(
                "application", application,
                "environment", environment));
    }

    /**
     * Spring MVC normally emits route templates instead of raw paths. This filter is the final
     * guardrail against an accidental unbounded URI tag exhausting Prometheus/Grafana.
     */
    @Bean
    MeterFilter httpUriCardinalityGuard() {
        return MeterFilter.maximumAllowableTags(
                "http.server.requests", "uri", 200, MeterFilter.deny());
    }
}
