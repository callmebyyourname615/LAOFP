package com.example.switching.aml.sanctions;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.switching.aml.config.AmlProperties;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

/** DB-backed freshness state used by Prometheus, health checks and startup validation. */
@Component
@Profile("!migration")
public class SanctionsFreshnessMonitor {

    public record ProviderStatus(String provider, boolean enabled, long activeRecords,
                                 Instant lastSuccess, long ageSeconds, boolean stale) {
    }

    private static final List<String> PROVIDERS = List.of("BOL", "OFAC", "UN");

    private final JdbcTemplate jdbcTemplate;
    private final AmlProperties properties;
    private final Map<String, AtomicLong> activeRecords = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> ageSeconds = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> lastSuccessEpoch = new ConcurrentHashMap<>();

    public SanctionsFreshnessMonitor(JdbcTemplate jdbcTemplate,
                                     AmlProperties properties,
                                     MeterRegistry meterRegistry) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
        for (String provider : PROVIDERS) {
            activeRecords.put(provider, new AtomicLong());
            ageSeconds.put(provider, new AtomicLong(-1));
            lastSuccessEpoch.put(provider, new AtomicLong());
            Gauge.builder("switching.aml.sanctions.active.records", activeRecords.get(provider), AtomicLong::get)
                    .tag("provider", provider).register(meterRegistry);
            Gauge.builder("switching.aml.sanctions.age.seconds", ageSeconds.get(provider), AtomicLong::get)
                    .tag("provider", provider).register(meterRegistry);
            Gauge.builder("switching.aml.sanctions.last.success.epoch", lastSuccessEpoch.get(provider), AtomicLong::get)
                    .tag("provider", provider).register(meterRegistry);
        }
        refresh();
    }

    @Scheduled(fixedDelayString = "${switching.aml.sanctions.metrics-refresh-interval:PT1M}")
    public void refresh() {
        for (String provider : PROVIDERS) {
            Long count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM sanctions_lists WHERE list_type = ? AND is_active = TRUE",
                    Long.class, provider);
            Timestamp timestamp = jdbcTemplate.queryForObject("""
                    SELECT MAX(completed_at)
                      FROM sanctions_import_runs
                     WHERE provider_code = ? AND status = 'SUCCESS'
                    """, Timestamp.class, provider);
            Instant success = timestamp == null ? null : timestamp.toInstant();
            activeRecords.get(provider).set(count == null ? 0 : count);
            lastSuccessEpoch.get(provider).set(success == null ? 0 : success.getEpochSecond());
            ageSeconds.get(provider).set(success == null
                    ? -1
                    : Math.max(0, Duration.between(success, Instant.now()).getSeconds()));
        }
    }

    public Map<String, ProviderStatus> currentStatuses() {
        Map<String, ProviderStatus> statuses = new LinkedHashMap<>();
        long maximumAge = properties.getSanctions().getMaximumAge().getSeconds();
        for (String provider : PROVIDERS) {
            long epoch = lastSuccessEpoch.get(provider).get();
            long age = ageSeconds.get(provider).get();
            statuses.put(provider, new ProviderStatus(
                    provider,
                    enabled(provider),
                    activeRecords.get(provider).get(),
                    epoch == 0 ? null : Instant.ofEpochSecond(epoch),
                    age,
                    age < 0 || age > maximumAge));
        }
        return Map.copyOf(statuses);
    }

    private boolean enabled(String provider) {
        return switch (provider) {
            case "BOL" -> properties.getSanctions().getBol().isEnabled();
            case "OFAC" -> properties.getSanctions().getOfac().isEnabled();
            case "UN" -> properties.getSanctions().getUn().isEnabled();
            default -> false;
        };
    }
}
