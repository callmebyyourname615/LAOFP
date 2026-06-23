package com.example.switching.usermgmt.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
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

@TestPropertySource(properties = {
        "switching.smos.enabled=true",
        "switching.smos.jwt-secret=test-smos-jwt-secret-with-more-than-32-characters",
        "switching.smos.bootstrap.enabled=false"
})
class SmosSessionSecurityIntegrationTest extends AbstractIntegrationTest {
    private static final String USERNAME = "phase61.session.operator";
    private static final String PASSWORD = "Phase61-Session-Token7!";
    @Autowired UserManagementService users;
    @Autowired AuthenticationService authentication;
    @Autowired TotpService totp;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM smos_auth_sessions WHERE user_id IN (SELECT id FROM smos_users WHERE username = ?)", USERNAME);
        jdbc.update("DELETE FROM smos_user_roles WHERE user_id IN (SELECT id FROM smos_users WHERE username = ?)", USERNAME);
        jdbc.update("DELETE FROM smos_users WHERE username = ?", USERNAME);
    }

    @Test
    void refreshReplayRevokesTheWholeSessionFamily() {
        UserResponse user = create();
        AuthResponse first = authenticate(user);
        AuthResponse rotated = authentication.refresh(first.refreshToken());

        assertThatThrownBy(() -> authentication.refresh(first.refreshToken()))
                .isInstanceOf(SmosAuthenticationException.class);
        assertThatThrownBy(() -> authentication.refresh(rotated.refreshToken()))
                .isInstanceOf(SmosAuthenticationException.class);
        assertThat(authentication.listSessions(USERNAME)).isEmpty();
    }

    @Test
    void operatorCanInventoryAndRevokeOwnRefreshSession() {
        UserResponse user = create();
        AuthResponse authenticated = authenticate(user);
        var sessions = authentication.listSessions(USERNAME);
        assertThat(sessions).hasSize(1);
        assertThat(sessions.getFirst().sessionFamilyId()).isNotNull();

        authentication.revokeSession(USERNAME, sessions.getFirst().id());
        assertThat(authentication.listSessions(USERNAME)).isEmpty();
        assertThatThrownBy(() -> authentication.refresh(authenticated.refreshToken()))
                .isInstanceOf(SmosAuthenticationException.class);
    }

    private UserResponse create() {
        return users.create(new CreateUserRequest(
                USERNAME, "phase61.session@example.test", "Phase 61 Session Operator", PASSWORD,
                Set.of(RoleType.READ_ONLY), true, null), "phase61-test");
    }
    private AuthResponse authenticate(UserResponse user) {
        AuthResponse challenge = authentication.login(USERNAME, PASSWORD);
        return authentication.verifyMfa(challenge.mfaToken(),
                totp.generate(user.mfaEnrollmentSecret(), Instant.now().getEpochSecond() / 30));
    }
}
