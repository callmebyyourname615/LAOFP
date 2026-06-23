package com.example.switching.usermgmt.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import org.junit.jupiter.api.Test;
import com.example.switching.usermgmt.entity.UserEntity;
import com.fasterxml.jackson.databind.ObjectMapper;

class SmosTokenServiceTest {
    private static final String SECRET = "test-smos-jwt-secret-with-more-than-32-characters";

    @Test
    void issuesAndValidatesNamespacedOperatorToken() {
        SmosTokenService service = new SmosTokenService(new ObjectMapper(), SECRET, 3600);
        UserEntity user = new UserEntity();
        user.setId(101L);
        user.setParticipantId(7L);
        user.setUsername("settlement.operator");

        String token = service.issue(user, Set.of("SETTLEMENT_OFFICER"),
                Set.of("settlement.view", "settlement.approve"));
        SmosTokenClaims claims = service.validate(token);

        assertThat(token).startsWith(SmosTokenService.TOKEN_PREFIX);
        assertThat(claims.userId()).isEqualTo(101L);
        assertThat(claims.username()).isEqualTo("settlement.operator");
        assertThat(claims.tokenId()).isNotBlank();
        assertThat(claims.participantId()).isEqualTo(7L);
        assertThat(claims.issuedAt()).isBefore(claims.expiresAt());
        assertThat(claims.roles()).containsExactly("SETTLEMENT_OFFICER");
        assertThat(claims.permissions()).contains("settlement.view", "settlement.approve");
    }

    @Test
    void rejectsTamperedTokenAndWeakSigningSecret() {
        SmosTokenService service = new SmosTokenService(new ObjectMapper(), SECRET, 3600);
        UserEntity user = new UserEntity();
        user.setId(102L);
        user.setUsername("risk.operator");
        String token = service.issue(user, Set.of("RISK_OFFICER"), Set.of("risk.view"));

        assertThatThrownBy(() -> service.validate(token.substring(0, token.length() - 2) + "xx"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SmosTokenService(new ObjectMapper(), "too-short", 3600))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 characters");
    }
}
