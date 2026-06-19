package com.example.switching.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class OperationalMetricsCollectorTest {

    @Test
    void refreshPublishesAllOperationalGaugesAndRefreshTimestamp() {
        JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(7L);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        Clock clock = Clock.fixed(Instant.parse("2026-06-17T07:00:00Z"), ZoneOffset.UTC);

        OperationalMetricsCollector collector =
                new OperationalMetricsCollector(jdbcTemplate, registry, clock);
        collector.refresh();

        assertThat(collector.value("switching.ops.transactions.pending")).isEqualTo(7L);
        assertThat(registry.get("switching.ops.webhook.failed.final").gauge().value())
                .isEqualTo(7.0);
        assertThat(registry.get("switching.ops.metrics.refresh.success").gauge().value())
                .isEqualTo(1.0);
        assertThat(registry.get("switching.ops.metrics.last.success.epoch").gauge().value())
                .isEqualTo(Instant.parse("2026-06-17T07:00:00Z").getEpochSecond());
    }

    @Test
    void failedRefreshRetainsLastKnownGoodValuesAndMarksRefreshFailed() {
        JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class)))
                .thenReturn(3L)
                .thenThrow(new IllegalStateException("database unavailable"));
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        OperationalMetricsCollector collector =
                new OperationalMetricsCollector(jdbcTemplate, registry, Clock.systemUTC());

        collector.refresh();

        assertThat(registry.get("switching.ops.metrics.refresh.success").gauge().value())
                .isZero();
        assertThat(collector.value("switching.ops.transactions.pending")).isZero();
    }
}
