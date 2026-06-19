package com.example.switching.observability;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Low-cardinality operational gauges backed by small indexed COUNT queries.
 *
 * <p>The collector deliberately keeps the last-known-good value when a refresh fails. A separate
 * success/age pair makes stale telemetry visible without turning a transient database issue into an
 * application outage.</p>
 */
@Component
@Profile("metrics")
public class OperationalMetricsCollector {

    private static final Logger log = LoggerFactory.getLogger(OperationalMetricsCollector.class);

    private static final Map<String, String> QUERIES = queries();

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;
    private final Map<String, AtomicLong> values = new LinkedHashMap<>();
    private final AtomicLong refreshSuccess = new AtomicLong(0);
    private final AtomicLong lastSuccessfulRefreshEpoch = new AtomicLong(0);

    public OperationalMetricsCollector(JdbcTemplate jdbcTemplate, MeterRegistry meterRegistry) {
        this(jdbcTemplate, meterRegistry, Clock.systemUTC());
    }

    OperationalMetricsCollector(JdbcTemplate jdbcTemplate, MeterRegistry meterRegistry, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;

        QUERIES.keySet().forEach(metricName -> {
            AtomicLong value = new AtomicLong(0);
            values.put(metricName, value);
            Gauge.builder(metricName, value, AtomicLong::get)
                    .description(description(metricName))
                    .register(meterRegistry);
        });

        Gauge.builder("switching.ops.metrics.refresh.success", refreshSuccess, AtomicLong::get)
                .description("1 when the most recent operational-metrics refresh succeeded")
                .register(meterRegistry);
        Gauge.builder("switching.ops.metrics.last.success.epoch", lastSuccessfulRefreshEpoch,
                        AtomicLong::get)
                .description("Unix epoch seconds of the last successful operational-metrics refresh")
                .register(meterRegistry);
    }

    @PostConstruct
    void initialRefresh() {
        refresh();
    }

    @Scheduled(fixedDelayString = "${switching.observability.operational-metrics-refresh:PT30S}")
    public void refresh() {
        Map<String, Long> snapshot = new LinkedHashMap<>();
        try {
            for (Map.Entry<String, String> query : QUERIES.entrySet()) {
                Long count = jdbcTemplate.queryForObject(query.getValue(), Long.class);
                snapshot.put(query.getKey(), count == null ? 0L : count);
            }
            snapshot.forEach((name, value) -> values.get(name).set(value));
            refreshSuccess.set(1);
            lastSuccessfulRefreshEpoch.set(Instant.now(clock).getEpochSecond());
        } catch (RuntimeException exception) {
            refreshSuccess.set(0);
            log.warn("Operational metrics refresh failed; retaining last-known-good values: {}",
                    exception.getMessage());
        }
    }

    long value(String metricName) {
        AtomicLong value = values.get(metricName);
        if (value == null) {
            throw new IllegalArgumentException("Unknown operational metric: " + metricName);
        }
        return value.get();
    }

    private static Map<String, String> queries() {
        Map<String, String> queries = new LinkedHashMap<>();
        queries.put("switching.ops.transactions.pending",
                "SELECT COUNT(*) FROM transactions "
                        + "WHERE status = 'ACCEPTED' AND business_date >= CURRENT_DATE - 1");
        queries.put("switching.ops.transactions.rejected.last5m",
                "SELECT COUNT(*) FROM transactions "
                        + "WHERE status = 'REJECTED' AND created_at >= NOW() - INTERVAL '5 minutes'");
        queries.put("switching.ops.inquiries.failed.last5m",
                "SELECT COUNT(*) FROM inquiries "
                        + "WHERE status = 'FAILED' AND created_at >= NOW() - INTERVAL '5 minutes'");
        queries.put("switching.ops.outbox.pending",
                "SELECT COUNT(*) FROM outbox_messages WHERE status IN ('PENDING', 'PROCESSING')");
        queries.put("switching.ops.outbox.failed",
                "SELECT COUNT(*) FROM outbox_messages WHERE status = 'FAILED'");
        queries.put("switching.ops.deadletter.unreviewed",
                "SELECT COUNT(*) FROM dead_letter_messages WHERE reviewed_at IS NULL");
        queries.put("switching.ops.settlement.failed.today",
                "SELECT COUNT(*) FROM settlement_cycles "
                        + "WHERE status = 'FAILED' AND settlement_date >= CURRENT_DATE");
        queries.put("switching.ops.reconciliation.unmatched",
                "SELECT COUNT(*) FROM reconciliation_items "
                        + "WHERE match_status IN ('UNMATCHED', 'DISPUTED') "
                        + "AND reconciliation_date >= CURRENT_DATE - 1");
        queries.put("switching.ops.aml.str.pending",
                "SELECT COUNT(*) FROM str_reports "
                        + "WHERE status IN ('PENDING_SUBMISSION', 'SUBMISSION_FAILED')");
        queries.put("switching.ops.webhook.pending.overdue",
                "SELECT COUNT(*) FROM webhook_delivery_log "
                        + "WHERE status = 'PENDING' AND created_at < NOW() - INTERVAL '5 minutes'");
        queries.put("switching.ops.webhook.failed.final",
                "SELECT COUNT(*) FROM webhook_delivery_log WHERE status = 'FAILED_FINAL'");
        queries.put("switching.ops.disputes.sla.overdue",
                "SELECT COUNT(*) FROM disputes "
                        + "WHERE status IN ('OPEN', 'UNDER_REVIEW', 'ESCALATED') "
                        + "AND sla_deadline < NOW()");
        queries.put("switching.ops.archive.failed.last24h",
                "SELECT COUNT(*) FROM archive_jobs "
                        + "WHERE status = 'FAILED' AND created_at >= NOW() - INTERVAL '24 hours'");
        return Map.copyOf(queries);
    }

    private static String description(String metricName) {
        return switch (metricName) {
            case "switching.ops.transactions.pending" -> "Accepted transactions awaiting final state";
            case "switching.ops.transactions.rejected.last5m" -> "Rejected transactions in five minutes";
            case "switching.ops.inquiries.failed.last5m" -> "Failed account inquiries in five minutes";
            case "switching.ops.outbox.pending" -> "Outbox messages pending or processing";
            case "switching.ops.outbox.failed" -> "Failed outbox messages awaiting review";
            case "switching.ops.deadletter.unreviewed" -> "Unreviewed dead-letter messages";
            case "switching.ops.settlement.failed.today" -> "Failed settlement cycles today";
            case "switching.ops.reconciliation.unmatched" -> "Recent unmatched reconciliation items";
            case "switching.ops.aml.str.pending" -> "STR reports pending or failed submission";
            case "switching.ops.webhook.pending.overdue" -> "Webhook deliveries pending over five minutes";
            case "switching.ops.webhook.failed.final" -> "Webhook deliveries in final-failure state";
            case "switching.ops.disputes.sla.overdue" -> "Open disputes beyond their SLA deadline";
            case "switching.ops.archive.failed.last24h" -> "Archive jobs failed in the last 24 hours";
            default -> metricName;
        };
    }
}
