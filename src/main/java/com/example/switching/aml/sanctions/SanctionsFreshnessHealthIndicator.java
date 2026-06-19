package com.example.switching.aml.sanctions;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.example.switching.aml.config.AmlProperties;

@Component("sanctionsFreshness")
@Profile("!migration")
public class SanctionsFreshnessHealthIndicator implements HealthIndicator {

    private final SanctionsFreshnessMonitor monitor;
    private final AmlProperties properties;

    public SanctionsFreshnessHealthIndicator(SanctionsFreshnessMonitor monitor,
                                             AmlProperties properties) {
        this.monitor = monitor;
        this.properties = properties;
    }

    @Override
    public Health health() {
        Map<String, Object> details = new LinkedHashMap<>();
        boolean unhealthy = false;
        for (SanctionsFreshnessMonitor.ProviderStatus status : monitor.currentStatuses().values()) {
            if (!status.enabled()) {
                continue;
            }
            details.put(status.provider(), Map.of(
                    "activeRecords", status.activeRecords(),
                    "lastSuccess", status.lastSuccess() == null ? "never" : status.lastSuccess().toString(),
                    "ageSeconds", status.ageSeconds(),
                    "stale", status.stale()));
            unhealthy |= status.activeRecords() == 0;
            unhealthy |= properties.getSanctions().isFailClosedOnStale() && status.stale();
        }
        return (unhealthy ? Health.down() : Health.up())
                .withDetail("providers", details)
                .withDetail("maximumAge", properties.getSanctions().getMaximumAge().toString())
                .build();
    }
}
