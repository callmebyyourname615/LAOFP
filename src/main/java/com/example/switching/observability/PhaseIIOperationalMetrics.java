package com.example.switching.observability;

import java.util.concurrent.atomic.AtomicLong;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import io.micrometer.core.instrument.MeterRegistry;

@Component
@Profile("!migration")
@ConditionalOnProperty(prefix = "switching.phase-ii", name = "enabled", havingValue = "true")
public class PhaseIIOperationalMetrics {
    private final JdbcTemplate jdbc;
    private final AtomicLong rtpPending = new AtomicLong();
    private final AtomicLong rtpExpired = new AtomicLong();
    private final AtomicLong promotionBudgetRemaining = new AtomicLong();
    private final AtomicLong railPending = new AtomicLong();
    private final AtomicLong reportDeliveryBacklog = new AtomicLong();

    public PhaseIIOperationalMetrics(JdbcTemplate jdbc, MeterRegistry registry) {
        this.jdbc = jdbc;
        registry.gauge("switching.phase2.rtp.pending", rtpPending);
        registry.gauge("switching.phase2.rtp.expired", rtpExpired);
        registry.gauge("switching.phase2.promotion.budget.remaining.minor_units", promotionBudgetRemaining);
        registry.gauge("switching.phase2.crossborder.pending", railPending);
        registry.gauge("switching.phase2.report.delivery.backlog", reportDeliveryBacklog);
    }

    @Scheduled(fixedDelayString = "${switching.phase-ii.metrics-refresh-ms:60000}")
    public void refresh() {
        rtpPending.set(count("SELECT count(*) FROM rtp_request WHERE status='PENDING_AUTH'"));
        rtpExpired.set(count("SELECT count(*) FROM rtp_request WHERE status='EXPIRED' AND updated_at>=now()-interval '24 hours'"));
        promotionBudgetRemaining.set(count("SELECT coalesce(min((budget_cap-budget_reserved-budget_consumed)*10000),0)::bigint FROM promotion WHERE status='ACTIVE'"));
        railPending.set(count("SELECT count(*) FROM cross_border_rail_message WHERE status IN ('PENDING','SUBMITTED','ACKNOWLEDGED')"));
        reportDeliveryBacklog.set(count("SELECT count(*) FROM report_delivery_run WHERE status IN ('QUEUED','RETRY','DELIVERING')"));
    }

    private long count(String sql) {
        Long value = jdbc.queryForObject(sql, Long.class);
        return value == null ? 0 : value;
    }
}
