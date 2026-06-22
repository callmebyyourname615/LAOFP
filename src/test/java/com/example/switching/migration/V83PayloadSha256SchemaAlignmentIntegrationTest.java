package com.example.switching.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Proves the real PostgreSQL V82 -> V83 upgrade path instead of only checking SQL text.
 *
 * <p>The test deliberately seeds rows while the columns are still {@code CHAR(64)},
 * applies only V83, and then verifies data preservation, PostgreSQL metadata, Flyway
 * history, validated constraints, and rejection of malformed digests.</p>
 */
@Testcontainers
class V83PayloadSha256SchemaAlignmentIntegrationTest {

    private static final String CONFIGURATION_HASH =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final String DEAD_LETTER_HASH =
            "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.9-alpine")
            .withDatabaseName("switching_v83")
            .withUsername("switching_v83")
            .withPassword("switching_v83");

    @Test
    void upgradesV82DataToValidatedVarchar64WithoutChangingDigestValues() throws Exception {
        Flyway throughV82 = flyway(MigrationVersion.fromVersion("82"));
        throughV82.migrate();

        try (Connection connection = connection()) {
            assertThat(columnType(connection, "configuration_change_requests"))
                    .isEqualTo("character(64)");
            assertThat(columnType(connection, "outbox_dead_letters"))
                    .isEqualTo("character(64)");
            seedV82Rows(connection);
            seedInvalidV82Row(connection);
        }

        Flyway throughLatest = flyway(null);
        assertThatThrownBy(throughLatest::migrate)
                .isInstanceOf(FlywayException.class)
                .hasMessageContaining("V83");

        // PostgreSQL transactional DDL must leave the database at V82 after the
        // fail-closed preflight rejects the malformed legacy digest.
        try (Connection connection = connection()) {
            assertThat(currentFlywayVersion(connection)).isEqualTo("82");
            assertThat(columnType(connection, "configuration_change_requests"))
                    .isEqualTo("character(64)");
            assertThat(columnType(connection, "outbox_dead_letters"))
                    .isEqualTo("character(64)");
            repairInvalidV82Row(connection);
        }

        throughLatest = flyway(null);
        throughLatest.migrate();
        throughLatest.validate();

        try (Connection connection = connection()) {
            assertThat(currentFlywayVersion(connection)).isEqualTo("84");

            assertVarchar64NotNull(connection, "configuration_change_requests");
            assertVarchar64NotNull(connection, "outbox_dead_letters");

            assertThat(payloadHash(connection,
                    "SELECT payload_sha256 FROM configuration_change_requests WHERE request_ref = 'CFG-V83-001'"))
                    .isEqualTo(CONFIGURATION_HASH)
                    .doesNotEndWith(" ");
            assertThat(payloadHash(connection,
                    "SELECT payload_sha256 FROM outbox_dead_letters WHERE event_id = 'EVENT-V83-001'"))
                    .isEqualTo(DEAD_LETTER_HASH)
                    .doesNotEndWith(" ");

            assertConstraintValidated(connection,
                    "configuration_change_requests", "ck_config_change_payload_sha256");
            assertConstraintValidated(connection,
                    "outbox_dead_letters", "ck_outbox_dlq_payload_sha256");

            assertThatThrownBy(() -> updateHash(connection,
                    "configuration_change_requests", "request_ref", "CFG-V83-001", "z".repeat(64)))
                    .isInstanceOf(SQLException.class)
                    .extracting(exception -> ((SQLException) exception).getSQLState())
                    .isEqualTo("23514");

            assertThatThrownBy(() -> updateHash(connection,
                    "outbox_dead_letters", "event_id", "EVENT-V83-001", "not-a-sha256"))
                    .isInstanceOf(SQLException.class)
                    .extracting(exception -> ((SQLException) exception).getSQLState())
                    .isEqualTo("23514");
        }

        // A second migrate must be a no-op and Flyway validation must remain clean.
        throughLatest.migrate();
        throughLatest.validate();
        assertThat(throughLatest.info().pending()).isEmpty();
    }

    private Flyway flyway(MigrationVersion target) {
        var configuration = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .validateOnMigrate(true)
                .cleanDisabled(true)
                .baselineOnMigrate(false);
        if (target != null) {
            configuration.target(target);
        }
        return configuration.load();
    }

    private Connection connection() throws SQLException {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static void seedV82Rows(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO configuration_change_requests (
                    request_ref, target_type, target_key, previous_value, desired_value,
                    payload_sha256, reason, ticket_reference, status, requested_by,
                    requested_at, expires_at
                ) VALUES (?, 'CONNECTOR_ENABLED', 'connector-v83', 'false', 'true', ?,
                          'V83 integration test', 'TICKET-V83', 'PENDING', 'phase53b-test',
                          CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '1 day')
                """)) {
            statement.setString(1, "CFG-V83-001");
            statement.setString(2, CONFIGURATION_HASH);
            statement.executeUpdate();
        }

        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO outbox_dead_letters (
                    event_id, payload_json, payload_sha256, failure_type, status,
                    failure_count, first_failed_at, last_failed_at
                ) VALUES (?, '{"phase":"53B"}', ?, 'DELIVERY_FAILURE', 'QUARANTINED',
                          1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """)) {
            statement.setString(1, "EVENT-V83-001");
            statement.setString(2, DEAD_LETTER_HASH);
            statement.executeUpdate();
        }
    }


    private static void seedInvalidV82Row(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO configuration_change_requests (
                    request_ref, target_type, target_key, previous_value, desired_value,
                    payload_sha256, reason, ticket_reference, status, requested_by,
                    requested_at, expires_at
                ) VALUES ('CFG-V83-INVALID', 'CONNECTOR_ENABLED', 'connector-v83-invalid',
                          'false', 'true', ?, 'V83 fail-closed integration test',
                          'TICKET-V83-INVALID', 'PENDING', 'phase53b-test',
                          CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '1 day')
                """)) {
            statement.setString(1, "z".repeat(64));
            statement.executeUpdate();
        }
    }

    private static void repairInvalidV82Row(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE configuration_change_requests
                SET payload_sha256 = ?
                WHERE request_ref = 'CFG-V83-INVALID'
                """)) {
            statement.setString(1, "fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210");
            assertThat(statement.executeUpdate()).isEqualTo(1);
        }
    }

    private static String columnType(Connection connection, String tableName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT format_type(attribute.atttypid, attribute.atttypmod)
                FROM pg_attribute attribute
                JOIN pg_class table_metadata ON table_metadata.oid = attribute.attrelid
                JOIN pg_namespace schema_metadata ON schema_metadata.oid = table_metadata.relnamespace
                WHERE schema_metadata.nspname = current_schema()
                  AND table_metadata.relname = ?
                  AND attribute.attname = 'payload_sha256'
                  AND NOT attribute.attisdropped
                """)) {
            statement.setString(1, tableName);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return resultSet.getString(1);
            }
        }
    }

    private static void assertVarchar64NotNull(Connection connection, String tableName)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT data_type, character_maximum_length, is_nullable
                FROM information_schema.columns
                WHERE table_schema = current_schema()
                  AND table_name = ?
                  AND column_name = 'payload_sha256'
                """)) {
            statement.setString(1, tableName);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getString("data_type")).isEqualTo("character varying");
                assertThat(resultSet.getInt("character_maximum_length")).isEqualTo(64);
                assertThat(resultSet.getString("is_nullable")).isEqualTo("NO");
            }
        }
    }

    private static void assertConstraintValidated(
            Connection connection, String tableName, String constraintName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT constraint_metadata.convalidated
                FROM pg_constraint constraint_metadata
                JOIN pg_class table_metadata ON table_metadata.oid = constraint_metadata.conrelid
                JOIN pg_namespace schema_metadata ON schema_metadata.oid = table_metadata.relnamespace
                WHERE schema_metadata.nspname = current_schema()
                  AND table_metadata.relname = ?
                  AND constraint_metadata.conname = ?
                  AND constraint_metadata.contype = 'c'
                """)) {
            statement.setString(1, tableName);
            statement.setString(2, constraintName);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getBoolean(1)).isTrue();
            }
        }
    }

    private static String payloadHash(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getString(1);
        }
    }

    private static String currentFlywayVersion(Connection connection) throws SQLException {
        return payloadHash(connection, """
                SELECT version
                FROM flyway_schema_history
                WHERE success = TRUE AND version IS NOT NULL
                ORDER BY installed_rank DESC
                LIMIT 1
                """);
    }

    private static void updateHash(
            Connection connection,
            String tableName,
            String keyColumn,
            String keyValue,
            String hash) throws SQLException {
        String sql = "UPDATE " + tableName + " SET payload_sha256 = ? WHERE " + keyColumn + " = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, hash);
            statement.setString(2, keyValue);
            statement.executeUpdate();
        }
    }
}
