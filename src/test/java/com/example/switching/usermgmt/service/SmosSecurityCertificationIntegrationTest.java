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
import com.example.switching.usermgmt.enums.RoleType;
import com.example.switching.usermgmt.enums.UserStatus;
import com.fasterxml.jackson.databind.ObjectMapper;

@TestPropertySource(properties = {
        "switching.smos.enabled=true",
        "switching.smos.jwt-secret=test-smos-jwt-secret-with-more-than-32-characters",
        "switching.smos.bootstrap.enabled=false",
        "switching.smos.max-failed-logins=5"
})
class SmosSecurityCertificationIntegrationTest extends AbstractIntegrationTest {
    private static final String PREFIX = "phase60.security.";
    private static final String PASSWORD = "Phase60-Secure-Operator7!";
    private static final String CYCLE_REF = "PHASE60-SEC-CYCLE";
    private static final String INSTRUCTION_REF = "PHASE60-SEC-INSTRUCTION";

    @Autowired UserManagementService userManagement;
    @Autowired AuthenticationService authentication;
    @Autowired TotpService totp;
    @Autowired MakerCheckerService makerChecker;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM smos_auth_sessions WHERE user_id IN (SELECT id FROM smos_users WHERE username LIKE ?)", PREFIX + "%");
        jdbc.update("DELETE FROM smos_maker_checker_requests WHERE maker_id IN (SELECT id FROM smos_users WHERE username LIKE ?) OR checker_id IN (SELECT id FROM smos_users WHERE username LIKE ?)", PREFIX + "%", PREFIX + "%");
        jdbc.update("DELETE FROM smos_user_roles WHERE user_id IN (SELECT id FROM smos_users WHERE username LIKE ?)", PREFIX + "%");
        jdbc.update("DELETE FROM smos_users WHERE username LIKE ?", PREFIX + "%");
        jdbc.update("DELETE FROM settlement_instructions WHERE instruction_ref = ?", INSTRUCTION_REF);
        jdbc.update("DELETE FROM settlement_cycles WHERE cycle_ref = ?", CYCLE_REF);
    }

    @Test
    void refreshTokensAreSingleUseAndLogoutRevokesTheReplacement() {
        UserResponse user = create("refresh", RoleType.SETTLEMENT_OFFICER);
        AuthResponse authenticated = authenticate(user);

        AuthResponse rotated = authentication.refresh(authenticated.refreshToken());
        assertThat(rotated.refreshToken()).isNotBlank().isNotEqualTo(authenticated.refreshToken());
        assertThatThrownBy(() -> authentication.refresh(authenticated.refreshToken()))
                .isInstanceOf(SmosAuthenticationException.class)
                .hasMessageContaining("invalid or expired");

        authentication.logout(rotated.refreshToken());
        assertThatThrownBy(() -> authentication.refresh(rotated.refreshToken()))
                .isInstanceOf(SmosAuthenticationException.class)
                .hasMessageContaining("invalid or expired");
    }

    @Test
    void repeatedInvalidMfaCodesLockTheUserAndRevokeTheChallenge() {
        UserResponse user = create("mfa-lockout", RoleType.RISK_OFFICER);
        AuthResponse challenge = authentication.login(user.username(), PASSWORD);
        String wrongCode = codeOutsideAllowedWindow(user.mfaEnrollmentSecret());

        for (int attempt = 1; attempt <= 5; attempt++) {
            assertThatThrownBy(() -> authentication.verifyMfa(challenge.mfaToken(), wrongCode))
                    .isInstanceOf(SmosAuthenticationException.class)
                    .hasMessageContaining("Invalid MFA code");
        }

        assertThat(jdbc.queryForObject(
                "SELECT status FROM smos_users WHERE username = ?", String.class, user.username()))
                .isEqualTo("LOCKED");
        assertThat(jdbc.queryForObject(
                "SELECT failed_login_count FROM smos_users WHERE username = ?", Integer.class, user.username()))
                .isEqualTo(5);
        assertThatThrownBy(() -> authentication.verifyMfa(challenge.mfaToken(), wrongCode))
                .isInstanceOf(SmosAuthenticationException.class)
                .hasMessageContaining("invalid or expired");
    }

    @Test
    void disablingAUserRevokesExistingRefreshTokens() {
        UserResponse user = create("disabled", RoleType.AUDITOR);
        AuthResponse authenticated = authenticate(user);

        userManagement.updateStatus(user.id(), UserStatus.DISABLED, "phase60-certifier");

        assertThatThrownBy(() -> authentication.refresh(authenticated.refreshToken()))
                .isInstanceOf(SmosAuthenticationException.class)
                .hasMessageContaining("invalid or expired");
        assertThatThrownBy(() -> authentication.login(user.username(), PASSWORD))
                .isInstanceOf(SmosAuthenticationException.class)
                .hasMessageContaining("not active");
    }

    @Test
    void makerCheckerRejectsPayloadTamperingBeforeControlledActionExecution() throws Exception {
        UserResponse maker = create("maker", RoleType.SETTLEMENT_OFFICER);
        UserResponse checker = create("checker", RoleType.SETTLEMENT_OFFICER);
        seedSettlementInstruction();

        var submitted = makerChecker.submit(
                SettlementApprovalActionHandler.TYPE,
                objectMapper.readTree("{\"instructionRef\":\"" + INSTRUCTION_REF + "\"}"),
                maker.username());
        jdbc.update("""
                UPDATE smos_maker_checker_requests
                SET payload_json = jsonb_set(payload_json, '{instructionRef}', to_jsonb('TAMPERED'::text))
                WHERE id = ?
                """, submitted.id());

        assertThatThrownBy(() -> makerChecker.approve(submitted.id(), checker.username(), "must not execute"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("integrity check failed");
        assertThat(jdbc.queryForObject(
                "SELECT status FROM settlement_instructions WHERE instruction_ref = ?", String.class, INSTRUCTION_REF))
                .isEqualTo("PENDING_APPROVAL");
    }

    @Test
    void authenticationAndAdministrationEmitChainedAuditEvents() {
        UserResponse user = create("audit", RoleType.READ_ONLY);
        authenticate(user);

        Integer eventCount = jdbc.queryForObject("""
                SELECT count(*) FROM audit_logs
                WHERE actor IN (?, 'phase60-certifier')
                  AND event_type IN ('SMOS_USER_CREATED', 'SMOS_LOGIN_SUCCEEDED')
                  AND entry_hash IS NOT NULL
                """, Integer.class, user.username());
        assertThat(eventCount).isGreaterThanOrEqualTo(2);
        Integer leakedSecretCount = jdbc.queryForObject("""
                SELECT count(*) FROM audit_logs
                WHERE actor IN (?, 'phase60-certifier')
                  AND payload ILIKE ?
                """, Integer.class, user.username(), "%" + PASSWORD + "%");
        assertThat(leakedSecretCount).isZero();
    }

    private UserResponse create(String suffix, RoleType role) {
        String username = PREFIX + suffix;
        return userManagement.create(new CreateUserRequest(
                username,
                suffix + "@phase60.example.test",
                "Phase 60 Security User",
                PASSWORD,
                Set.of(role),
                true,
                null), "phase60-certifier");
    }

    private AuthResponse authenticate(UserResponse user) {
        AuthResponse challenge = authentication.login(user.username(), PASSWORD);
        long counter = Instant.now().getEpochSecond() / 30;
        return authentication.verifyMfa(
                challenge.mfaToken(),
                totp.generate(user.mfaEnrollmentSecret(), counter));
    }

    private String codeOutsideAllowedWindow(String secret) {
        long counter = Instant.now().getEpochSecond() / 30;
        Set<String> valid = Set.of(
                totp.generate(secret, counter - 1),
                totp.generate(secret, counter),
                totp.generate(secret, counter + 1));
        for (int candidate = 0; candidate < 1_000_000; candidate++) {
            String value = "%06d".formatted(candidate);
            if (!valid.contains(value)) return value;
        }
        throw new IllegalStateException("Unable to select an invalid TOTP code");
    }

    private void seedSettlementInstruction() {
        Long cycleId = jdbc.queryForObject("""
                INSERT INTO settlement_cycles(cycle_ref, settlement_date, cycle_number, status, opened_at, closed_at)
                VALUES (?, ?, 32061, 'CLOSED', now(), now())
                ON CONFLICT (cycle_ref) DO UPDATE SET status = 'CLOSED'
                RETURNING id
                """, Long.class, CYCLE_REF, LocalDate.now());
        jdbc.update("""
                INSERT INTO settlement_instructions(
                    instruction_ref, cycle_id, debtor_psp_id, creditor_psp_id,
                    currency, net_amount, status)
                VALUES (?, ?, 'BANK_A', 'BANK_B', 'LAK', 100000, 'PENDING_APPROVAL')
                ON CONFLICT (instruction_ref) DO UPDATE
                SET status = 'PENDING_APPROVAL', approved_by = NULL, approved_at = NULL
                """, INSTRUCTION_REF, cycleId);
    }
}
