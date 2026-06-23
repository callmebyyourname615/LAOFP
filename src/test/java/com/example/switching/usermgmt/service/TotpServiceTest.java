package com.example.switching.usermgmt.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class TotpServiceTest {
    private static final Instant NOW = Instant.parse("2026-06-22T03:00:00Z");
    private final TotpService service = new TotpService(
            new SecureRandom(), Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void generatedCodeVerifiesWithinCurrentWindow() {
        String secret = "JBSWY3DPEHPK3PXP";
        String code = service.generate(secret, NOW.getEpochSecond() / 30);

        assertThat(code).matches("\\d{6}");
        assertThat(service.verify(secret, code)).isTrue();
    }

    @Test
    void rejectsMalformedCodesAndSecrets() {
        assertThat(service.verify("JBSWY3DPEHPK3PXP", "12345")).isFalse();
        assertThat(service.verify(null, "123456")).isFalse();
    }

    @Test
    void base32RoundTripPreservesSecretBytes() {
        byte[] original = new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
        assertThat(TotpService.decodeBase32(TotpService.encodeBase32(original)))
                .containsExactly(original);
    }
}
