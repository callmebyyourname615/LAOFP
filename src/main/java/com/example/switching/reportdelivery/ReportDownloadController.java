package com.example.switching.reportdelivery;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/reports/download")
@ConditionalOnProperty(
        prefix = "switching.phase-ii.report-delivery",
        name = "enabled",
        havingValue = "true")
public class ReportDownloadController {

    private final ReportArtifactService artifacts;
    private final Environment environment;

    public ReportDownloadController(ReportArtifactService artifacts, Environment environment) {
        this.artifacts = artifacts;
        this.environment = environment;
    }

    @GetMapping("/{id}")
    public ResponseEntity<byte[]> download(
            @PathVariable UUID id,
            @RequestParam long expires,
            @RequestParam String token) {
        long now = Instant.now().getEpochSecond();
        if (expires < now || expires > now + 86_400L) {
            throw new IllegalArgumentException("Download link is expired or has an invalid lifetime");
        }
        String secret = environment.getProperty(
                "switching.phase-ii.report-delivery.link-signing-secret");
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("Report link signing secret is not configured");
        }
        String expected = EmailLinkDeliveryService.hmac(id + "|" + expires, secret);
        if (!constantTimeHexEquals(expected, token)) {
            throw new SecurityException("Invalid download token");
        }

        ReportArtifactService.StoredArtifact artifact = artifacts.get(id);
        String filename = sanitizeFilename(artifact.fileName());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(artifact.contentType()))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"")
                .body(artifact.content());
    }

    private static boolean constantTimeHexEquals(String expected, String supplied) {
        try {
            byte[] expectedBytes = HexFormat.of().parseHex(expected);
            byte[] suppliedBytes = HexFormat.of().parseHex(supplied);
            return MessageDigest.isEqual(expectedBytes, suppliedBytes);
        } catch (IllegalArgumentException exception) {
            // Keep token failures indistinguishable and avoid reflecting token input.
            return MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.US_ASCII),
                    new byte[expected.length()]);
        }
    }

    private static String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "report.bin";
        }
        String sanitized = filename.replaceAll("[^A-Za-z0-9._-]", "_");
        return sanitized.contains("..") ? "report.bin" : sanitized;
    }
}
