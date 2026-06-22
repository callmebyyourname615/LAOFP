package com.example.switching.reportdelivery;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class SettlementReportArtifactGenerator implements ReportArtifactGenerator {

    private final JdbcTemplate jdbc;

    public SettlementReportArtifactGenerator(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String reportType() {
        return "CAMT054";
    }

    @Override
    public ReportArtifact generate(
            String participant,
            String generationKey) {
        if (participant == null
                || !participant.matches("[A-Za-z0-9_-]{2,64}")) {
            throw new IllegalArgumentException("Invalid report participant");
        }
        if (generationKey == null || generationKey.isBlank()) {
            throw new IllegalArgumentException("Report generation key is required");
        }
        String content = jdbc.queryForObject("""
                SELECT content
                  FROM settlement_reports
                 WHERE psp_id=?
                 ORDER BY generated_at DESC
                 LIMIT 1
                """, String.class, participant);
        if (content == null) {
            throw new IllegalStateException("No settlement report is available");
        }
        String safeGenerationId = sha256(generationKey).substring(0, 20);
        return new ReportArtifact(
                reportType(),
                participant,
                generationKey,
                "camt054-" + participant + "-" + safeGenerationId + ".xml",
                "application/xml",
                content.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
