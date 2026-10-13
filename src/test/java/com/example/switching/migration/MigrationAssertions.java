package com.example.switching.migration;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;

final class MigrationAssertions {

    static final String LATEST_VERSION = "117";
    static final long SUCCESSFUL_MIGRATION_COUNT = 110L;

    private MigrationAssertions() {
    }

    static void assertCleanInstallAtHead(JdbcTemplate jdbc) {
        assertThat(currentVersion(jdbc)).isEqualTo(LATEST_VERSION);
        assertThat(successfulMigrationCount(jdbc)).isEqualTo(SUCCESSFUL_MIGRATION_COUNT);
    }

    static void assertCleanInstallAtHead(Flyway flyway) {
        assertThat(flyway.info().current()).isNotNull();
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo(LATEST_VERSION);
        assertThat(flyway.info().pending()).isEmpty();
    }

    static void assertMigrationApplied(JdbcTemplate jdbc, String version) {
        assertThat(jdbc.queryForObject("""
                SELECT count(*)
                  FROM flyway_schema_history
                 WHERE version = ?
                   AND success
                """, Integer.class, version)).isEqualTo(1);
    }

    private static String currentVersion(JdbcTemplate jdbc) {
        return jdbc.queryForObject("""
                SELECT version
                  FROM flyway_schema_history
                 WHERE success
                   AND version IS NOT NULL
                 ORDER BY installed_rank DESC
                 LIMIT 1
                """, String.class);
    }

    private static long successfulMigrationCount(JdbcTemplate jdbc) {
        return jdbc.queryForObject("""
                SELECT count(*)
                  FROM flyway_schema_history
                 WHERE success
                   AND version IS NOT NULL
                """, Long.class);
    }
}
