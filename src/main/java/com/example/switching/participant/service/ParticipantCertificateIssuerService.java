package com.example.switching.participant.service;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.example.switching.participant.dto.IssueCertificateRequest;
import com.example.switching.participant.dto.IssueCertificateResponse;
import com.example.switching.participant.exception.ParticipantNotFoundException;
import com.example.switching.participant.repository.ParticipantRepository;
import com.example.switching.security.mtls.MtlsCertificateValidator;

@Service
public class ParticipantCertificateIssuerService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final ParticipantRepository participantRepository;
    private final MtlsCertificateValidator certValidator;
    private final String opensslBinary;
    private final String caCertPath;
    private final String caKeyPath;
    private final int validityDays;

    public ParticipantCertificateIssuerService(
            ParticipantRepository participantRepository,
            MtlsCertificateValidator certValidator,
            @Value("${switching.certificates.issuer.openssl-bin:openssl}") String opensslBinary,
            @Value("${switching.certificates.issuer.ca-cert-path:}") String caCertPath,
            @Value("${switching.certificates.issuer.ca-key-path:}") String caKeyPath,
            @Value("${switching.certificates.issuer.validity-days:365}") int validityDays) {
        this.participantRepository = participantRepository;
        this.certValidator = certValidator;
        this.opensslBinary = opensslBinary;
        this.caCertPath = caCertPath;
        this.caKeyPath = caKeyPath;
        this.validityDays = validityDays;
    }

    public IssueCertificateResponse issue(String pspId, IssueCertificateRequest request) {
        requireParticipant(pspId);
        String normalizedPspId = pspId.toUpperCase(Locale.ROOT);
        String csrPem = normalizeCsr(request == null ? null : request.csrPem());
        requireIssuerConfigured();

        try {
            Path workDir = Files.createTempDirectory("switching-csr-issue-");
            try {
                Path csrPath = workDir.resolve(normalizedPspId + ".csr");
                Path certPath = workDir.resolve(normalizedPspId + ".crt");
                Path extPath = workDir.resolve("client-auth.ext");
                Files.writeString(csrPath, csrPem, StandardCharsets.UTF_8);
                Files.writeString(extPath, """
                        basicConstraints=critical,CA:FALSE
                        keyUsage=critical,digitalSignature,keyEncipherment
                        extendedKeyUsage=clientAuth
                        subjectKeyIdentifier=hash
                        authorityKeyIdentifier=keyid,issuer
                        """, StandardCharsets.UTF_8);

                String serial = randomSerialHex();
                List<String> command = List.of(
                        opensslBinary, "x509", "-req",
                        "-in", csrPath.toString(),
                        "-CA", caCertPath,
                        "-CAkey", caKeyPath,
                        "-set_serial", "0x" + serial,
                        "-out", certPath.toString(),
                        "-days", String.valueOf(validityDays),
                        "-sha256",
                        "-extfile", extPath.toString());
                Process process = new ProcessBuilder(command)
                        .directory(workDir.toFile())
                        .redirectErrorStream(true)
                        .start();
                String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                boolean finished = process.waitFor(15, TimeUnit.SECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    throw new IllegalArgumentException("CSR signing timed out");
                }
                if (process.exitValue() != 0) {
                    throw new IllegalArgumentException("CSR signing failed: " + output.trim());
                }

                String certPem = Files.readString(certPath, StandardCharsets.UTF_8).trim() + "\n";
                X509Certificate cert = certValidator.parseCertificate(certPem);
                return new IssueCertificateResponse(
                        normalizedPspId,
                        normalizedPspId.toLowerCase(Locale.ROOT) + "-client.crt",
                        certPem,
                        cert.getSubjectX500Principal().getName(),
                        cert.getIssuerX500Principal().getName(),
                        cert.getSerialNumber().toString(16).toUpperCase(Locale.ROOT),
                        toLocalDateTime(cert.getNotAfter().toInstant()));
            } finally {
                deleteQuietly(workDir);
            }
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Could not issue certificate from CSR: " + exception.getMessage(), exception);
        }
    }

    private void requireParticipant(String pspId) {
        participantRepository.findByBankCode(pspId.toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new ParticipantNotFoundException(pspId));
    }

    private void requireIssuerConfigured() {
        if (isBlank(caCertPath) || isBlank(caKeyPath)) {
            throw new IllegalArgumentException("Certificate issuer CA is not configured");
        }
        if (!Files.isReadable(Path.of(caCertPath))) {
            throw new IllegalArgumentException("CA certificate is not readable: " + caCertPath);
        }
        if (!Files.isReadable(Path.of(caKeyPath))) {
            throw new IllegalArgumentException("CA private key is not readable: " + caKeyPath);
        }
    }

    private static String normalizeCsr(String csrPem) {
        if (isBlank(csrPem)) {
            throw new IllegalArgumentException("CSR PEM is required");
        }
        String normalized = csrPem.replace("\r\n", "\n").trim();
        if (!normalized.matches("(?s).*-----BEGIN (NEW )?CERTIFICATE REQUEST-----.+-----END (NEW )?CERTIFICATE REQUEST-----.*")) {
            throw new IllegalArgumentException("CSR must include BEGIN CERTIFICATE REQUEST and END CERTIFICATE REQUEST lines");
        }
        return normalized + "\n";
    }

    private static String randomSerialHex() {
        return new BigInteger(159, RANDOM).abs().toString(16).toUpperCase(Locale.ROOT);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static LocalDateTime toLocalDateTime(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }

    private static void deleteQuietly(Path path) {
        if (path == null || !Files.exists(path)) return;
        try (var stream = Files.walk(path)) {
            stream.sorted((left, right) -> right.compareTo(left)).forEach(candidate -> {
                try {
                    Files.deleteIfExists(candidate);
                } catch (Exception ignored) {
                }
            });
        } catch (Exception ignored) {
        }
    }
}
