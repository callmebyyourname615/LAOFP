package com.example.switching.security.oauth.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.example.switching.security.oauth.OAuthTokenInvalidException;
import com.example.switching.security.oauth.entity.OAuthClientEntity;
import com.example.switching.security.oauth.enums.OAuthClientStatus;
import com.example.switching.security.oauth.enums.OAuthClientTier;
import com.example.switching.security.oauth.repository.OAuthClientRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

class OAuthTokenServiceRotationTest {

    @Test
    void tokenIssuedAfterRotationInSameSecondRemainsValid() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-21T08:00:00.100Z"));
        OAuthClientRepository repository = Mockito.mock(OAuthClientRepository.class);
        OAuthClientEntity client = activeClient();
        when(repository.findById("client-a")).thenReturn(Optional.of(client));

        OAuthTokenService service = new OAuthTokenService(
                repository,
                new ObjectMapper(),
                clock,
                "unit-test-oauth-secret-with-at-least-32-characters",
                3600);

        String oldToken = service.createToken("client-a", Set.of("payments:read"));
        service.markClientRotated("client-a", Instant.parse("2026-07-21T08:00:00.250Z").toEpochMilli());

        clock.setInstant(Instant.parse("2026-07-21T08:00:00.500Z"));
        String newToken = service.createToken("client-a", Set.of("payments:read"));

        assertThatThrownBy(() -> service.validateToken(oldToken))
                .isInstanceOf(OAuthTokenInvalidException.class)
                .hasMessageContaining("credential rotation");
        assertThatCode(() -> service.validateToken(newToken)).doesNotThrowAnyException();
    }

    private static OAuthClientEntity activeClient() {
        OAuthClientEntity client = new OAuthClientEntity();
        client.setClientId("client-a");
        client.setPspId("BANK_A");
        client.setScopes("payments:read");
        client.setStatus(OAuthClientStatus.ACTIVE);
        client.setTier(OAuthClientTier.TIER1);
        return client;
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void setInstant(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
