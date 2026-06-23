package com.example.switching.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Boots the dedicated migration process against real PostgreSQL and proves that it applies the
 * complete schema while remaining isolated from application runtime infrastructure.
 */
@Testcontainers
class MigrationApplicationIntegrationTest {

    private static final String LOCAL_MASTER_KEY =
            "NImwCmFwkSIeDgy8UJtzGq86A389puEEe6gi2Wdo9MM=";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.9-alpine")
            .withDatabaseName("switching_migration")
            .withUsername("switching_migration")
            .withPassword("switching_migration");

    @Test
    void appliesAllMigrationsThroughLatestVersionWithoutLoadingKafkaOrRuntimeWorkers() {
        String[] args = new String[] {
                "--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                "--spring.datasource.username=" + POSTGRES.getUsername(),
                "--spring.datasource.password=" + POSTGRES.getPassword(),
                "--spring.flyway.url=" + POSTGRES.getJdbcUrl(),
                "--spring.flyway.user=" + POSTGRES.getUsername(),
                "--spring.flyway.password=" + POSTGRES.getPassword(),
                "--spring.flyway.enabled=false",
                "--switching.webhook.encryption.provider=local",
                "--switching.webhook.encryption.local.master-key-base64=" + LOCAL_MASTER_KEY,
                "--spring.main.banner-mode=off",
                "--logging.level.root=WARN",
        };

        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(MigrationApplication.class)
                .profiles("migration")
                .web(WebApplicationType.NONE)
                .run(args)) {

            Flyway flyway = Flyway.configure()
                    .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                    .locations("classpath:db/migration")
                    .cleanDisabled(true)
                    .load();

            assertThat(flyway.info().current()).isNotNull();
            assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("106");
            assertThat(flyway.info().pending()).isEmpty();
            flyway.validate();

            assertThat(context.getBeansOfType(KafkaTemplate.class)).isEmpty();
            assertThat(context.containsBean("outboxDispatchWorker")).isFalse();
            assertThat(context.containsBean("outboxRecoveryWorker")).isFalse();
            assertThat(context.containsBean("archiveWorkerService")).isFalse();
            assertThat(context.containsBean("settlementCutoffScheduler")).isFalse();
            assertThat(context.containsBean("webhookRetryService")).isFalse();
            assertThat(context.containsBean("operationalMetricsCollector")).isFalse();
        }
    }
}
