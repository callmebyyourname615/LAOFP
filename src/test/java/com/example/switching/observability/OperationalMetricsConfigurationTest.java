package com.example.switching.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;

class OperationalMetricsConfigurationTest {

    private final ApplicationContextRunner runtimeContext = new ApplicationContextRunner()
            .withUserConfiguration(OperationalMetricsConfiguration.class)
            .withBean(JdbcTemplate.class, OperationalMetricsConfigurationTest::jdbcTemplate)
            .withBean(MeterRegistry.class, SimpleMeterRegistry::new);

    @Test
    void collectorIsEnabledByDefaultForRuntimeProfiles() {
        runtimeContext
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("prod"))
                .run(context -> {
                    assertThat(context).hasSingleBean(OperationalMetricsCollector.class);
                    assertThat(context.getBean(MeterRegistry.class)
                            .find("switching.ops.metrics.refresh.success").gauge()).isNotNull();
                });
    }

    @Test
    void collectorCanBeExplicitlyDisabledForDiagnosticRuntime() {
        runtimeContext
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("prod"))
                .withPropertyValues("switching.observability.operational-metrics.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(OperationalMetricsCollector.class));
    }

    @Test
    void collectorIsNeverRegisteredForMigrationProfile() {
        runtimeContext
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("migration"))
                .run(context -> assertThat(context).doesNotHaveBean(OperationalMetricsCollector.class));
    }

    private static JdbcTemplate jdbcTemplate() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(0L);
        return jdbc;
    }
}
