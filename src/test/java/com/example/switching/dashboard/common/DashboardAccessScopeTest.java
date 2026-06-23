package com.example.switching.dashboard.common;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class DashboardAccessScopeTest {
    private final DashboardAccessScope scope = new DashboardAccessScope();

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void internalOperatorIsAllowed() {
        var auth = new UsernamePasswordAuthenticationToken("operator", null, java.util.List.of());
        auth.setDetails(Map.of("userId", 1L));
        SecurityContextHolder.getContext().setAuthentication(auth);
        assertThatCode(scope::requireSchemeWideOperator).doesNotThrowAnyException();
    }

    @Test
    void participantScopedOperatorIsRejected() {
        var auth = new UsernamePasswordAuthenticationToken("participant", null, java.util.List.of());
        auth.setDetails(Map.of("userId", 2L, "participantId", 99L));
        SecurityContextHolder.getContext().setAuthentication(auth);
        assertThatThrownBy(scope::requireSchemeWideOperator)
                .isInstanceOf(AccessDeniedException.class);
    }
}
