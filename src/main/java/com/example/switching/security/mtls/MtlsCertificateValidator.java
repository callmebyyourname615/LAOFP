package com.example.switching.security.mtls;

import java.io.ByteArrayInputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * P9 — mTLS client-certificate validator.
 *
 * <p>In production, nginx (or the load balancer) terminates TLS and injects
 * the client's X.509 certificate into the {@code ssl-client-cert} HTTP header
 * as a URL-encoded PEM string
 * ({@code ingress-nginx ssl-client-cert}).
 *
 * <p>This validator:
 * <ol>
 *   <li>URL-decodes and parses the PEM certificate.</li>
 *   <li>Computes its SHA-256 fingerprint from the DER-encoded bytes.</li>
 *   <li>Looks up the fingerprint in {@code psp_certificates}.</li>
 *   <li>Rejects with {@link MtlsCertInvalidException} (LFP-2002) if:
 *     <ul>
 *       <li>the certificate cannot be parsed,</li>
 *       <li>the fingerprint is not registered,</li>
 *       <li>the record's {@code status} is not {@code ACTIVE}, or</li>
 *       <li>the record's {@code expires_at} is in the past.</li>
 *     </ul>
 *   </li>
 * </ol>
 */
@Component
public class MtlsCertificateValidator {

    private static final Logger log = LoggerFactory.getLogger(MtlsCertificateValidator.class);

    private final JdbcTemplate jdbcTemplate;

    public MtlsCertificateValidator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Validates the client certificate carried in the header value.
     *
     * @param certHeaderValue raw header value (URL-encoded PEM from nginx, or plain PEM)
     * @throws MtlsCertInvalidException if the cert is missing, invalid, not registered,
     *                                   revoked, or expired
     */
    public void validate(String certHeaderValue) {
        String fingerprint = extractFingerprint(certHeaderValue);
        verifyRegistered(fingerprint);
    }

    /**
     * Validates a pre-computed SHA-256 fingerprint directly against
     * {@code psp_certificates}.
     *
     * <p>Used in integration tests to verify the DB-lookup path without
     * requiring a full PEM certificate to be present in the request.
     *
     * @throws MtlsCertInvalidException if the fingerprint is not registered,
     *                                   revoked, or expired
     */
    public void validateByFingerprint(String fingerprint) {
        verifyRegistered(fingerprint);
    }

    /**
     * Parses a plain (non-URL-encoded) PEM string and returns its SHA-256 hex fingerprint.
     * Used by {@code ParticipantCredentialService.registerCertificate()} when storing a
     * new certificate in {@code psp_certificates}.
     *
     * @throws MtlsCertInvalidException if the PEM cannot be parsed
     */
    public String computeFingerprint(String pem) {
        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate cert = (X509Certificate) cf.generateCertificate(
                    new ByteArrayInputStream(pem.getBytes(StandardCharsets.UTF_8)));
            byte[] derBytes = cert.getEncoded();
            byte[] hashBytes = MessageDigest.getInstance("SHA-256").digest(derBytes);
            return HexFormat.of().formatHex(hashBytes);
        } catch (MtlsCertInvalidException ex) {
            throw ex;
        } catch (Exception ex) {
            log.debug("Failed to compute fingerprint from PEM: {}", ex.getMessage());
            throw new MtlsCertInvalidException("Cannot parse certificate PEM: " + ex.getMessage());
        }
    }

    /**
     * Parses a plain PEM string and returns the {@link X509Certificate}.
     * Used by {@code ParticipantCredentialService} to extract subject DN and expiry date.
     *
     * @throws MtlsCertInvalidException if the PEM cannot be parsed
     */
    public X509Certificate parseCertificate(String pem) {
        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            return (X509Certificate) cf.generateCertificate(
                    new ByteArrayInputStream(pem.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            log.debug("Failed to parse PEM certificate: {}", ex.getMessage());
            throw new MtlsCertInvalidException("Cannot parse certificate PEM: " + ex.getMessage());
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /**
     * Parses the header value, computes and returns the SHA-256 hex fingerprint.
     */
    String extractFingerprint(String certHeaderValue) {
        try {
            // nginx URL-encodes the PEM; tolerate both encoded and plain PEM
            String pem = URLDecoder.decode(certHeaderValue.trim(), StandardCharsets.UTF_8);

            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate cert = (X509Certificate) cf.generateCertificate(
                    new ByteArrayInputStream(pem.getBytes(StandardCharsets.UTF_8)));

            byte[] derBytes = cert.getEncoded();
            byte[] hashBytes = MessageDigest.getInstance("SHA-256").digest(derBytes);
            return HexFormat.of().formatHex(hashBytes);

        } catch (MtlsCertInvalidException ex) {
            throw ex;
        } catch (Exception ex) {
            log.debug("Failed to parse mTLS client certificate: {}", ex.getMessage());
            throw new MtlsCertInvalidException("Invalid mTLS client certificate: " + ex.getMessage());
        }
    }

    /**
     * Looks up the fingerprint in {@code psp_certificates}.
     * Throws if not found, not ACTIVE, or expired.
     */
    private void verifyRegistered(String fingerprint) {
        List<String> rows = jdbcTemplate.query(
                """
                SELECT status
                FROM psp_certificates
                WHERE cert_fingerprint = ?
                """,
                (rs, rowNum) -> rs.getString("status"),
                fingerprint);

        if (rows.isEmpty()) {
            log.debug("mTLS cert fingerprint not registered: {}", fingerprint);
            throw new MtlsCertInvalidException("mTLS client certificate is not registered");
        }

        String status = rows.get(0);
        if (!"ACTIVE".equalsIgnoreCase(status)) {
            log.debug("mTLS cert rejected — status={} fingerprint={}", status, fingerprint);
            throw new MtlsCertInvalidException("mTLS client certificate has been revoked");
        }

        // Check expiry from DB record
        List<LocalDateTime> expiry = jdbcTemplate.query(
                """
                SELECT expires_at
                FROM psp_certificates
                WHERE cert_fingerprint = ?
                  AND status = 'ACTIVE'
                """,
                (rs, rowNum) -> rs.getTimestamp("expires_at").toLocalDateTime(),
                fingerprint);

        if (!expiry.isEmpty() && LocalDateTime.now().isAfter(expiry.get(0))) {
            log.debug("mTLS cert expired: fingerprint={}", fingerprint);
            throw new MtlsCertInvalidException("mTLS client certificate has expired");
        }
    }
}
