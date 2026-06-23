package com.example.switching.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Production certification for the empty-database V1 through V83 path. */
@Testcontainers
class V83CleanInstallCertificationIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.9-alpine")
            .withDatabaseName("switching_clean_v83")
            .withUsername("switching_clean_v83")
            .withPassword("switching_clean_v83");

    @Test
    void cleanDatabaseMigratesAndValidatesAtVersion83() throws Exception {
        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .validateOnMigrate(true)
                .cleanDisabled(true)
                .baselineOnMigrate(false)
                .load();

        var result = flyway.migrate();
        flyway.validate();

        assertThat(result.success).isTrue();
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("100");
        assertThat(flyway.info().pending()).isEmpty();

        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            assertThat(queryLong(connection,
                    "SELECT count(*) FROM flyway_schema_history WHERE success AND version IS NOT NULL"))
                    .isEqualTo(95L);
            assertThat(queryLong(connection,
                    "SELECT count(*) FROM flyway_schema_history WHERE NOT success"))
                    .isZero();
            assertThat(queryLong(connection, """
                    SELECT count(*)
                    FROM information_schema.columns
                    WHERE table_schema = 'public'
                      AND table_name IN ('configuration_change_requests', 'outbox_dead_letters')
                      AND column_name = 'payload_sha256'
                      AND data_type = 'character varying'
                      AND character_maximum_length = 64
                      AND is_nullable = 'NO'
                    """)).isEqualTo(2L);
            assertThat(queryLong(connection, """
                    SELECT count(*)
                    FROM pg_constraint
                    WHERE conname IN ('ck_config_change_payload_sha256', 'ck_outbox_dlq_payload_sha256')
                      AND contype = 'c'
                      AND convalidated
                    """)).isEqualTo(2L);
        }
    }

    private static long queryLong(Connection connection, String sql) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getLong(1);
        }
    }
}
