package com.example.switching.usermgmt.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import com.example.switching.AbstractIntegrationTest;
import com.example.switching.usermgmt.dto.AuthResponse;
import com.example.switching.usermgmt.dto.CreateUserRequest;
import com.example.switching.usermgmt.dto.UserResponse;
import com.example.switching.usermgmt.enums.MakerCheckerStatus;
import com.example.switching.usermgmt.enums.RoleType;
import com.fasterxml.jackson.databind.ObjectMapper;

@TestPropertySource(properties = {
        "switching.smos.enabled=true",
        "switching.smos.jwt-secret=test-smos-jwt-secret-with-more-than-32-characters",
        "switching.smos.bootstrap.enabled=false"
})
class SmosUserManagementIntegrationTest extends AbstractIntegrationTest {
    private static final String USERNAME = "smos.integration.operator";
    private static final String MAKER = "smos.integration.maker";
    private static final String CHECKER = "smos.integration.checker";
    private static final String READ_ONLY = "smos.integration.readonly";
    private static final String PASSWORD = "Integration-Secure-2026!";
    private static final String CYCLE_REF = "SMOS-IT-CYCLE";
    private static final String INSTRUCTION_REF = "SMOS-IT-INSTRUCTION";

    @Autowired UserManagementService userManagement;
    @Autowired AuthenticationService authentication;
    @Autowired SmosTokenService tokens;
    @Autowired TotpService totp;
    @Autowired MakerCheckerService makerChecker;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM smos_auth_sessions WHERE user_id IN (SELECT id FROM smos_users WHERE username LIKE 'smos.integration.%')");
        jdbc.update("DELETE FROM smos_maker_checker_requests WHERE maker_id IN (SELECT id FROM smos_users WHERE username LIKE 'smos.integration.%') OR checker_id IN (SELECT id FROM smos_users WHERE username LIKE 'smos.integration.%')");
        jdbc.update("DELETE FROM smos_user_roles WHERE user_id IN (SELECT id FROM smos_users WHERE username LIKE 'smos.integration.%')");
        jdbc.update("DELETE FROM smos_users WHERE username LIKE 'smos.integration.%'");
        jdbc.update("DELETE FROM settlement_instructions WHERE instruction_ref = ?", INSTRUCTION_REF);
        jdbc.update("DELETE FROM settlement_cycles WHERE cycle_ref = ?", CYCLE_REF);
    }

    @Test
    void createLoginMfaAndRefreshFlowUsesSeededRbac() {
        UserResponse created = userManagement.create(new CreateUserRequest(
                USERNAME,
                "smos.integration@example.test",
                "SMOS Integration Operator",
                PASSWORD,
                Set.of(RoleType.SETTLEMENT_OFFICER),
                true,
                null), "integration-test");

        assertThat(created.mfaEnrollmentSecret()).isNotBlank();
        AuthResponse challenge = authentication.login(USERNAME, PASSWORD);
        assertThat(challenge.mfaRequired()).isTrue();

        long counter = Instant.now().getEpochSecond() / 30;
        String code = totp.generate(created.mfaEnrollmentSecret(), counter);
        AuthResponse authenticated = authentication.verifyMfa(challenge.mfaToken(), code);

        assertThat(authenticated.accessToken()).startsWith(SmosTokenService.TOKEN_PREFIX);
        assertThat(authenticated.roles()).contains("SETTLEMENT_OFFICER");
        assertThat(authenticated.permissions()).contains("settlement.view", "settlement.approve");
        assertThat(tokens.validate(authenticated.accessToken()).username()).isEqualTo(USERNAME);

        AuthResponse refreshed = authentication.refresh(authenticated.refreshToken());
        assertThat(refreshed.accessToken()).startsWith(SmosTokenService.TOKEN_PREFIX);
    }
    @Test
    void failedPasswordIsPersistedForLockoutAccounting() {
        createUser(USERNAME, "smos.login-failure@example.test", RoleType.SETTLEMENT_OFFICER);

        assertThatThrownBy(() -> authentication.login(USERNAME, "wrong-password"))
                .isInstanceOf(SmosAuthenticationException.class);

        Integer failures = jdbc.queryForObject(
                "SELECT failed_login_count FROM smos_users WHERE username = ?", Integer.class, USERNAME);
        assertThat(failures).isEqualTo(1);
    }

    @Test
    void makerCheckerRejectsSelfApprovalAndExecutesApprovedSettlementAction() throws Exception {
        createUser(MAKER, "smos.maker@example.test", RoleType.SETTLEMENT_OFFICER);
        createUser(CHECKER, "smos.checker@example.test", RoleType.SETTLEMENT_OFFICER);
        seedSettlementInstruction();

        var submitted = makerChecker.submit(SettlementApprovalActionHandler.TYPE,
                objectMapper.readTree("{\"instructionRef\":\"" + INSTRUCTION_REF + "\",\"note\":\"approved in integration test\"}"), MAKER);
        assertThat(submitted.status()).isEqualTo(MakerCheckerStatus.PENDING);

        assertThatThrownBy(() -> makerChecker.approve(submitted.id(), MAKER, "self approval must fail"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("different users");

        var approved = makerChecker.approve(submitted.id(), CHECKER, "four-eyes approval");
        assertThat(approved.status()).isEqualTo(MakerCheckerStatus.APPROVED);
        assertThat(jdbc.queryForObject(
                "SELECT status FROM settlement_instructions WHERE instruction_ref = ?", String.class, INSTRUCTION_REF))
                .isEqualTo("APPROVED");
    }

    @Test
    void readOnlyRoleCannotSubmitControlledAction() throws Exception {
        createUser(READ_ONLY, "smos.readonly@example.test", RoleType.READ_ONLY);
        seedSettlementInstruction();

        assertThatThrownBy(() -> makerChecker.submit(SettlementApprovalActionHandler.TYPE,
                objectMapper.readTree("{\"instructionRef\":\"" + INSTRUCTION_REF + "\"}"), READ_ONLY))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Missing permission");
    }

    private UserResponse createUser(String username, String email, RoleType role) {
        return userManagement.create(new CreateUserRequest(
                username, email, "SMOS Integration User", PASSWORD, Set.of(role), true, null), "integration-test");
    }

    private void seedSettlementInstruction() {
        Long cycleId = jdbc.queryForObject("""
                INSERT INTO settlement_cycles(cycle_ref, settlement_date, cycle_number, status, opened_at, closed_at)
                VALUES (?, ?, 97, 'CLOSED', now(), now())
                ON CONFLICT (cycle_ref) DO UPDATE SET status = 'CLOSED'
                RETURNING id
                """, Long.class, CYCLE_REF, LocalDate.now());
        jdbc.update("""
                INSERT INTO settlement_instructions(
                    instruction_ref, cycle_id, debtor_psp_id, creditor_psp_id, currency, net_amount, status)
                VALUES (?, ?, 'BANK_A', 'BANK_B', 'LAK', 100000, 'PENDING_APPROVAL')
                ON CONFLICT (instruction_ref) DO UPDATE SET status = 'PENDING_APPROVAL', approved_by = NULL, approved_at = NULL
                """, INSTRUCTION_REF, cycleId);
    }

}
