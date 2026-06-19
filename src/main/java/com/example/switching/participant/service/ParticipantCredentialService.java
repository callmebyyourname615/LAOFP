package com.example.switching.participant.service;

import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.switching.participant.dto.RegisterCertificateRequest;
import com.example.switching.participant.dto.RegisterCertificateResponse;
import com.example.switching.participant.dto.RotateCredentialsResponse;
import com.example.switching.participant.exception.ParticipantNotFoundException;
import com.example.switching.participant.repository.ParticipantRepository;
import com.example.switching.security.mtls.MtlsCertificateValidator;
import com.example.switching.security.oauth.entity.OAuthClientEntity;
import com.example.switching.security.oauth.repository.OAuthClientRepository;
import com.example.switching.security.oauth.service.OAuthTokenService;
import com.example.switching.security.util.ApiKeyHashUtil;

/**
 * P9 Step 4 — Credential management for PSP participants.
 *
 * <p>Provides three operations:
 * <ol>
 *   <li>{@link #rotateCredentials(String)} — generates a new {@code client_secret},
 *       hashes it, persists it to {@code oauth_clients}, and invalidates all
 *       previously-issued Bearer tokens for the client via
 *       {@link OAuthTokenService#markClientRotated(String, long)}.</li>
 *   <li>{@link #registerCertificate(String, RegisterCertificateRequest)} — parses the
 *       submitted PEM cert, computes its SHA-256 fingerprint, and inserts a new
 *       {@code ACTIVE} row in {@code psp_certificates}.</li>
 *   <li>{@link #revokeCertificate(String)} — sets the cert record to
 *       {@code REVOKED}, causing subsequent mTLS validation to reject that
 *       fingerprint.</li>
 * </ol>
 */
@Service
public class ParticipantCredentialService {

    private static final Logger log = LoggerFactory.getLogger(ParticipantCredentialService.class);

    private final ParticipantRepository        participantRepository;
    private final OAuthClientRepository        oauthClientRepository;
    private final OAuthTokenService            tokenService;
    private final MtlsCertificateValidator     certValidator;
    private final JdbcTemplate                 jdbcTemplate;

    public ParticipantCredentialService(
            ParticipantRepository    participantRepository,
            OAuthClientRepository    oauthClientRepository,
            OAuthTokenService        tokenService,
            MtlsCertificateValidator certValidator,
            JdbcTemplate             jdbcTemplate) {
        this.participantRepository = participantRepository;
        this.oauthClientRepository = oauthClientRepository;
        this.tokenService          = tokenService;
        this.certValidator         = certValidator;
        this.jdbcTemplate          = jdbcTemplate;
    }

    // ── rotateCredentials ────────────────────────────────────────────────────

    /**
     * Generates a new {@code client_secret} for the first active OAuth client
     * belonging to {@code pspId}.  Hashes and persists it, then invalidates all
     * tokens the client issued before this moment.
     *
     * @return response containing the plain-text secret (returned once only)
     * @throws ParticipantNotFoundException if no participant with {@code pspId} exists
     * @throws IllegalStateException        if no OAuth client is registered for the PSP
     */
    @Transactional
    public RotateCredentialsResponse rotateCredentials(String pspId) {
        requireParticipant(pspId);

        List<OAuthClientEntity> clients = oauthClientRepository.findByPspId(pspId);
        if (clients.isEmpty()) {
            throw new IllegalStateException("No OAuth client registered for PSP: " + pspId);
        }

        // Rotate the first (primary) client for this PSP.
        OAuthClientEntity client = clients.get(0);

        String newSecret     = ApiKeyHashUtil.generate();   // sk-{64 hex chars}
        String newSecretHash = ApiKeyHashUtil.hash(newSecret);

        client.setClientSecretHash(newSecretHash);
        oauthClientRepository.save(client);

        // Invalidate all tokens issued at or before now.
        long rotationEpoch = Instant.now().getEpochSecond();
        tokenService.markClientRotated(client.getClientId(), rotationEpoch);

        log.info("Credentials rotated for pspId={} clientId={}", pspId, client.getClientId());

        return new RotateCredentialsResponse(
                client.getClientId(),
                newSecret,
                pspId,
                client.getExpiresAt());
    }

    // ── registerCertificate ──────────────────────────────────────────────────

    /**
     * Parses the PEM certificate in the request, computes its SHA-256 fingerprint,
     * and inserts an {@code ACTIVE} record in {@code psp_certificates}.
     *
     * @throws ParticipantNotFoundException if no participant with {@code pspId} exists
     * @throws com.example.switching.security.mtls.MtlsCertInvalidException
     *         if the PEM cannot be parsed
     */
    @Transactional
    public RegisterCertificateResponse registerCertificate(
            String pspId,
            RegisterCertificateRequest request) {

        requireParticipant(pspId);

        String pem         = request.certPem();
        String fingerprint = certValidator.computeFingerprint(pem);
        X509Certificate x509 = certValidator.parseCertificate(pem);

        String subjectDn  = x509.getSubjectX500Principal().getName();
        LocalDateTime issuedAt  = toLocalDateTime(x509.getNotBefore().toInstant());
        LocalDateTime expiresAt = toLocalDateTime(x509.getNotAfter().toInstant());

        String certId = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();

        jdbcTemplate.update("""
                INSERT INTO psp_certificates
                    (cert_id, psp_id, cert_fingerprint, subject_dn, issued_at, expires_at, status, created_at)
                VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', ?)
                ON CONFLICT (cert_fingerprint) DO UPDATE SET status = 'ACTIVE', expires_at = EXCLUDED.expires_at
                """,
                certId, pspId, fingerprint, subjectDn, issuedAt, expiresAt, now);

        log.info("Certificate registered for pspId={} fingerprint={}", pspId, fingerprint);

        return new RegisterCertificateResponse(certId, pspId, fingerprint, subjectDn, expiresAt);
    }

    // ── revokeCertificate ────────────────────────────────────────────────────

    /**
     * Sets the {@code status} of the certificate record to {@code REVOKED}.
     * Subsequent mTLS validation for that fingerprint will return 401 LFP-2002.
     *
     * @param certId UUID of the {@code psp_certificates} row
     * @throws IllegalArgumentException if no such certificate exists
     */
    @Transactional
    public void revokeCertificate(String certId) {
        int rows = jdbcTemplate.update("""
                UPDATE psp_certificates
                SET status = 'REVOKED'
                WHERE cert_id = ?
                """, certId);

        if (rows == 0) {
            throw new IllegalArgumentException("Certificate not found: " + certId);
        }

        log.info("Certificate revoked: certId={}", certId);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void requireParticipant(String pspId) {
        participantRepository.findByBankCode(pspId.toUpperCase())
                .orElseThrow(() -> new ParticipantNotFoundException(pspId));
    }

    private static LocalDateTime toLocalDateTime(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }
}
