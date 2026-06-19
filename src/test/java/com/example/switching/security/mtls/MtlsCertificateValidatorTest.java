package com.example.switching.security.mtls;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/**
 * Unit tests for {@link MtlsCertificateValidator} — DB-lookup path (TC-MTLS-001 – 007).
 *
 * <p>Only the {@link MtlsCertificateValidator#validateByFingerprint} and
 * {@link MtlsCertificateValidator#extractFingerprint} error paths are tested here.
 * The PEM-parsing / fingerprint-computation paths are already exercised by
 * {@code MtlsValidationIntegrationTest} using a real embedded certificate.
 *
 * Tests:
 * - extractFingerprint: garbage input → MtlsCertInvalidException
 * - extractFingerprint: empty/blank → MtlsCertInvalidException
 * - validateByFingerprint: not in DB → "not registered"
 * - validateByFingerprint: REVOKED → "revoked"
 * - validateByFingerprint: ACTIVE + past expiry → "expired"
 * - validateByFingerprint: ACTIVE + future expiry → no exception
 * - validateByFingerprint: ACTIVE + no expiry row → no exception
 */
@ExtendWith(MockitoExtension.class)
class MtlsCertificateValidatorTest {

    @Mock JdbcTemplate jdbcTemplate;

    MtlsCertificateValidator validator;

    static final String ANY_FINGERPRINT =
            "aabbcc112233445566778899aabbcc112233445566778899aabbcc1122334455";

    @BeforeEach
    void setUp() {
        validator = new MtlsCertificateValidator(jdbcTemplate);
    }

    // ── TC-MTLS-001 — extractFingerprint: garbage PEM ─────────────────────────

    @Test
    void extractFingerprint_garbage_throwsMtlsCertInvalid() {
        assertThrows(MtlsCertInvalidException.class,
                () -> validator.extractFingerprint("not-a-certificate"),
                "Garbage input must throw MtlsCertInvalidException");
    }

    // ── TC-MTLS-002 — extractFingerprint: blank header ────────────────────────

    @Test
    void extractFingerprint_blank_throwsMtlsCertInvalid() {
        assertThrows(MtlsCertInvalidException.class,
                () -> validator.extractFingerprint("   "),
                "Blank header must throw MtlsCertInvalidException");
    }

    // ── TC-MTLS-003 — not registered ─────────────────────────────────────────

    @SuppressWarnings("unchecked")
    @Test
    void validateByFingerprint_notRegistered_throwsMtlsCertInvalid() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any()))
                .thenReturn(List.of());   // empty → not found

        MtlsCertInvalidException ex = assertThrows(MtlsCertInvalidException.class,
                () -> validator.validateByFingerprint(ANY_FINGERPRINT));
        assertTrue(ex.getMessage().contains("not registered"),
                "Error must say 'not registered'");
    }

    // ── TC-MTLS-004 — REVOKED cert ────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    @Test
    void validateByFingerprint_revoked_throwsMtlsCertInvalid() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any()))
                .thenReturn(List.of("REVOKED"));

        MtlsCertInvalidException ex = assertThrows(MtlsCertInvalidException.class,
                () -> validator.validateByFingerprint(ANY_FINGERPRINT));
        assertTrue(ex.getMessage().contains("revoked"),
                "Error must mention 'revoked'");
    }

    // ── TC-MTLS-005 — ACTIVE but expired ─────────────────────────────────────

    @SuppressWarnings("unchecked")
    @Test
    void validateByFingerprint_activeButExpired_throwsMtlsCertInvalid() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any()))
                .thenReturn(List.of("ACTIVE"))
                .thenReturn(List.of(LocalDateTime.now().minusDays(1)));

        MtlsCertInvalidException ex = assertThrows(MtlsCertInvalidException.class,
                () -> validator.validateByFingerprint(ANY_FINGERPRINT));
        assertTrue(ex.getMessage().contains("expired"),
                "Error must mention 'expired'");
    }

    // ── TC-MTLS-006 — ACTIVE + future expiry passes ───────────────────────────

    @SuppressWarnings("unchecked")
    @Test
    void validateByFingerprint_activeWithFutureExpiry_doesNotThrow() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any()))
                .thenReturn(List.of("ACTIVE"))
                .thenReturn(List.of(LocalDateTime.now().plusYears(5)));

        assertDoesNotThrow(() -> validator.validateByFingerprint(ANY_FINGERPRINT),
                "ACTIVE cert with future expiry must not throw");
    }

    // ── TC-MTLS-007 — ACTIVE + no expiry row (perpetual) ─────────────────────

    @SuppressWarnings("unchecked")
    @Test
    void validateByFingerprint_activeNoExpiryRow_doesNotThrow() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any()))
                .thenReturn(List.of("ACTIVE"))
                .thenReturn(List.of());   // no expiry record → treat as no-expiry

        assertDoesNotThrow(() -> validator.validateByFingerprint(ANY_FINGERPRINT),
                "ACTIVE cert with no expiry row must not throw");
    }
}
