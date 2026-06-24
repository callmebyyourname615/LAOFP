package com.example.switching.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import com.example.switching.AbstractIntegrationTest;

class V97SmosUserAccessMigrationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void cleanInstallContainsV97SmosSchemaAndSeededPermissionMatrix() {
        Integer migrationCount = jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE success", Integer.class);
        // version is VARCHAR — MAX does lex compare ("97" > "106"); use installed_rank.
        String currentVersion = jdbc.queryForObject(
                "SELECT version FROM flyway_schema_history WHERE success "
                        + "ORDER BY installed_rank DESC LIMIT 1", String.class);

        assertThat(migrationCount).isEqualTo(99);
        assertThat(currentVersion).isEqualTo("106");

        List<String> roles = jdbc.queryForList(
                "SELECT name FROM smos_roles ORDER BY name", String.class);
        assertThat(roles).containsExactlyInAnyOrder(
                "SYSTEM_ADMIN",
                "OPS_ADMIN",
                "SETTLEMENT_OFFICER",
                "DISPUTE_OFFICER",
                "RISK_OFFICER",
                "AUDITOR",
                "PARTICIPANT_ADMIN",
                "READ_ONLY");

        Integer permissionCount = jdbc.queryForObject(
                "SELECT count(*) FROM smos_permissions", Integer.class);
        Integer rolesWithoutPermissions = jdbc.queryForObject("""
                SELECT count(*)
                FROM smos_roles r
                LEFT JOIN smos_role_permissions rp ON rp.role_id = r.id
                WHERE rp.role_id IS NULL
                """, Integer.class);
        Integer systemAdminPermissionCount = jdbc.queryForObject("""
                SELECT count(*)
                FROM smos_role_permissions rp
                JOIN smos_roles r ON r.id = rp.role_id
                WHERE r.name = 'SYSTEM_ADMIN'
                """, Integer.class);

        assertThat(permissionCount).isEqualTo(20);
        assertThat(rolesWithoutPermissions).isZero();
        assertThat(systemAdminPermissionCount).isEqualTo(permissionCount);

        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name IN (
                    'smos_users', 'smos_roles', 'smos_permissions',
                    'smos_user_roles', 'smos_role_permissions',
                    'smos_auth_sessions', 'smos_maker_checker_requests')
                """, Integer.class)).isEqualTo(7);
    }
}
