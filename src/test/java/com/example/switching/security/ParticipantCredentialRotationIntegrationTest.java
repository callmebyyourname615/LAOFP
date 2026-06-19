package com.example.switching.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.example.switching.AbstractIntegrationTest;
import com.example.switching.participant.dto.RegisterCertificateRequest;
import com.example.switching.participant.dto.RegisterCertificateResponse;
import com.example.switching.participant.dto.RotateCredentialsResponse;
import com.example.switching.participant.service.ParticipantCredentialService;
import com.example.switching.security.mtls.MtlsCertInvalidException;
import com.example.switching.security.mtls.MtlsCertificateValidator;
import com.example.switching.security.oauth.OAuthTokenInvalidException;
import com.example.switching.security.oauth.service.OAuthTokenService;

/**
 * P9 Step 4 — ParticipantCredentialService integration tests.
 *
 * TC-CR-001  Rotate secret → old Bearer token is immediately rejected (LFP-2001)
 * TC-CR-002  Register cert PEM → fingerprint accepted by MtlsCertificateValidator
 * TC-CR-003  Register then revoke cert → fingerprint rejected with LFP-2002
 * TC-CR-004  SUSPENDED oauth_client → Bearer token returns 403 LFP-2004
 */
@TestPropertySource(properties = {
        "switching.security.api-key.enabled=true",
        "switching.security.oauth.enabled=true",
        "switching.security.mtls.enabled=false"
})
class ParticipantCredentialRotationIntegrationTest extends AbstractIntegrationTest {

    // ── Valid X.509 PEM for TC-CR-002 (generated with openssl req -x509 -newkey rsa:2048) ──
    // openssl x509 -noout -fingerprint -sha256 →
    //   41f82ec6da7fd8e7e29fba6916637e5a89e0e21e698eab3b9a845ec5184f19ca
    private static final String VALID_CERT_PEM =
            "-----BEGIN CERTIFICATE-----\n" +
            "MIIC7DCCAdQCCQDmfSv9mMqYJzANBgkqhkiG9w0BAQsFADA4MRkwFwYDVQQDDBBC\n" +
            "QU5LX0FfTVRMU19URVNUMQ4wDAYDVQQKDAVMYW9GUDELMAkGA1UEBhMCTEEwHhcN\n" +
            "MjYwNTE5MDQzMzM2WhcNMzYwNTE2MDQzMzM2WjA4MRkwFwYDVQQDDBBCQU5LX0Ff\n" +
            "TVRMU19URVNUMQ4wDAYDVQQKDAVMYW9GUDELMAkGA1UEBhMCTEEwggEiMA0GCSqG\n" +
            "SIb3DQEBAQUAA4IBDwAwggEKAoIBAQCmq9VI0MYDRS5S/as8JWWALqa5up8bzAty\n" +
            "yEFqM5tMms6cCrBjr80B8Bp/Ayy0iJfhzOJ3UG/f0sFEih24w8IfUBwueLzBFmc7\n" +
            "Kzd/k157bOqfHmUXzMv1UbbhB1ZwfYxVctjlnd6EarAMu3xj17mntfuGT1S9Bjvq\n" +
            "ZUzGdKt2lnkG9pUUF+xw9YE7CwhzDsasggrjuHFuJ12FrDSZKJTNOw91heCoKv7d\n" +
            "uyikOa48hItW1A/QX6wiKWcQsa623PV7Ee6BmwuqcZPmhZOTfzWJ/BHKayTKI7Sv\n" +
            "hG5XW+69DpOwQ8nm03hauc7Dq89g0qpXJXJ00SLov+hSr2zyweNxAgMBAAEwDQYJ\n" +
            "KoZIhvcNAQELBQADggEBABuv7ywlW5NUfHzn3/kBGD4eog970wYXYW/W+mzmaDAG\n" +
            "qNs1jW/6XHteajNgc+zidEUJ6ODHrOhKxeWwWu/Egmav+xmejYQqsjZGQFpH1QXX\n" +
            "SpQWb0JGSrOJYVc6Pa5bbC/g5JEDE5wolpvca0v3lZYH+kd85QxeHCNCFm6NExB3\n" +
            "py5TmKQ3HjU3DjB2UtxPqK5H5FyBxHrIuwUrRezkYHjWJarVoQc09eHtb9WsTNWs\n" +
            "mTB7CMuYYz09ZwhlTYlY+Hks5KYNYtHS9uXY/CllhObpVmV/C3bsfXflf126yac2\n" +
            "i2TtN0YPDmDfj059n4arG5WMYzmxmFB+HSCe5N/w22c=\n" +
            "-----END CERTIFICATE-----\n";

    // Seeded PSP IDs (V15 migration)
    private static final String PSP_A = "BANK_A";
    private static final String PSP_B = "BANK_B";

    // Seeded oauth_client IDs (V20 migration)
    private static final String CLIENT_A = "client-bank-a";
    private static final String CLIENT_B = "client-bank-b";

    // ADMIN API key (V14 migration)
    private static final String ADMIN_KEY = "sk-admin-switching-2026";

    @Autowired private WebApplicationContext         webApplicationContext;
    @Autowired private FilterChainProxy             springSecurityFilterChain;
    @Autowired private JdbcTemplate                 jdbcTemplate;
    @Autowired private OAuthTokenService            tokenService;
    @Autowired private MtlsCertificateValidator     certValidator;
    @Autowired private ParticipantCredentialService credentialService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .addFilters(springSecurityFilterChain)
                .build();

        // Restore both clients to ACTIVE + reset secrets to seed values
        jdbcTemplate.update(
                "UPDATE oauth_clients SET status = 'ACTIVE', client_secret_hash = encode(digest(?::bytea, 'sha256'), 'hex') WHERE client_id = ?",
                "secret-bank-a-switching-2026", CLIENT_A);
        jdbcTemplate.update(
                "UPDATE oauth_clients SET status = 'ACTIVE', client_secret_hash = encode(digest(?::bytea, 'sha256'), 'hex') WHERE client_id = ?",
                "secret-bank-b-switching-2026", CLIENT_B);

        // Remove test-only cert rows
        jdbcTemplate.update(
                "DELETE FROM psp_certificates WHERE subject_dn LIKE '%CR_TEST%'");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TC-CR-001  Rotate secret → pre-rotation Bearer token immediately rejected
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void rotateCredentials_invalidatesOldToken() {
        // Issue token BEFORE rotation
        String oldToken = tokenService.createToken(CLIENT_A, null);
        assertNotNull(oldToken);

        // Sanity: token is valid before rotation
        assertDoesNotThrow(() -> tokenService.validateToken(oldToken));

        // Rotate — returns plain-text secret exactly once
        RotateCredentialsResponse resp = credentialService.rotateCredentials(PSP_A);
        assertNotNull(resp.clientSecret(), "Plain-text secret must be returned once");

        // Pre-rotation token must now be rejected
        assertThrows(OAuthTokenInvalidException.class,
                () -> tokenService.validateToken(oldToken),
                "Pre-rotation token must be rejected after credential rotation");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TC-CR-002  Register cert PEM → fingerprint accepted by MtlsCertificateValidator
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void registerCertificate_fingerprintAccepted() {
        // Use the URL-decoded plain PEM (same cert as MtlsValidationIntegrationTest)
        RegisterCertificateResponse resp = credentialService.registerCertificate(
                PSP_A, new RegisterCertificateRequest(VALID_CERT_PEM));

        assertNotNull(resp.certId(),      "certId must not be null");
        assertNotNull(resp.fingerprint(), "fingerprint must not be null");
        assertEquals(PSP_A, resp.pspId(), "pspId must match");

        // Fingerprint must now be accepted by the mTLS validator
        assertDoesNotThrow(() -> certValidator.validateByFingerprint(resp.fingerprint()),
                "Registered fingerprint must be accepted by MtlsCertificateValidator");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TC-CR-003  Register cert → revoke → fingerprint rejected (LFP-2002)
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void revokeCertificate_fingerprintRejected() {
        // Seed a synthetic ACTIVE cert row directly (avoids needing a real PEM)
        String certId      = UUID.randomUUID().toString();
        // Build a deterministic 64-char hex fingerprint from two UUIDs
        String fp = UUID.randomUUID().toString().replace("-", "")    // 32 hex chars
                  + UUID.randomUUID().toString().replace("-", "");   // 32 hex chars → total 64
        LocalDateTime now = LocalDateTime.now();

        jdbcTemplate.update("""
                INSERT INTO psp_certificates
                    (cert_id, psp_id, cert_fingerprint, subject_dn, issued_at, expires_at, status, created_at)
                VALUES (?, ?, ?, 'CN=CR_TEST', ?, ?, 'ACTIVE', ?)
                ON CONFLICT (cert_fingerprint) DO UPDATE SET status = 'ACTIVE'
                """,
                certId, PSP_A, fp, now, now.plusYears(10), now);

        // Fingerprint is accepted while ACTIVE
        assertDoesNotThrow(() -> certValidator.validateByFingerprint(fp),
                "ACTIVE fingerprint must be accepted before revocation");

        // Revoke the cert
        credentialService.revokeCertificate(certId);

        // Fingerprint must now be rejected
        assertThrows(MtlsCertInvalidException.class,
                () -> certValidator.validateByFingerprint(fp),
                "Revoked fingerprint must be rejected with MtlsCertInvalidException");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TC-CR-004  SUSPENDED oauth_client → Bearer token returns 403 LFP-2004
    //
    // Uses CLIENT_B (never rotated) to avoid the in-memory rotation-epoch
    // set by TC-CR-001 for CLIENT_A invalidating the token before activeClient()
    // can detect SUSPENDED status.
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void suspendedClient_bearerTokenReturns403() throws Exception {
        // Issue a valid token for CLIENT_B while it is still ACTIVE
        String token = tokenService.createToken(CLIENT_B, null);

        // Suspend CLIENT_B directly in DB
        jdbcTemplate.update(
                "UPDATE oauth_clients SET status = 'SUSPENDED' WHERE client_id = ?", CLIENT_B);

        // Bearer token for a SUSPENDED client must return 403 LFP-2004
        mockMvc.perform(
                post("/api/transfers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("LFP-2004"));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /** Wraps a Java string as a JSON string literal with minimal escaping. */
    private static String jsonString(String value) {
        return "\"" + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r") + "\"";
    }
}
