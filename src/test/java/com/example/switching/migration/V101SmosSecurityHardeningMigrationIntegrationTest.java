package com.example.switching.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import com.example.switching.AbstractIntegrationTest;

class V101SmosSecurityHardeningMigrationIntegrationTest extends AbstractIntegrationTest {
    @Autowired JdbcTemplate jdbc;

    @Test
    void cleanInstallContainsV101IdentityAndSessionHardening() {
        assertThat(jdbc.queryForObject(
                "SELECT max(version) FROM flyway_schema_history WHERE success", String.class)).isEqualTo("106");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE success", Integer.class)).isEqualTo(99);

        List<String> userColumns = jdbc.queryForList("""
                SELECT column_name FROM information_schema.columns
                WHERE table_schema='public' AND table_name='smos_users'
                """, String.class);
        assertThat(userColumns).contains(
                "participant_id", "password_changed_at", "mfa_enrolled_at", "locked_at", "last_failed_login_at");

        List<String> sessionColumns = jdbc.queryForList("""
                SELECT column_name FROM information_schema.columns
                WHERE table_schema='public' AND table_name='smos_auth_sessions'
                """, String.class);
        assertThat(sessionColumns).contains(
                "session_family_id", "rotated_from_id", "last_used_at", "client_fingerprint_hash");

        Integer permissionCount = jdbc.queryForObject("SELECT count(*) FROM smos_permissions", Integer.class);
        assertThat(permissionCount).isEqualTo(20);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM smos_permissions
                WHERE (resource, action) IN (
                    ('session','view'),('session','revoke'),
                    ('security','reset_mfa'),('security','reset_password'))
                """, Integer.class)).isEqualTo(4);
    }
}
