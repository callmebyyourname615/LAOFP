package com.example.switching.reportdelivery;

import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportArtifactService {

    private final JdbcTemplate jdbc;
    private final Map<String, ReportArtifactGenerator> generators;

    public ReportArtifactService(
            JdbcTemplate jdbc,
            List<ReportArtifactGenerator> generators) {
        this.jdbc = jdbc;
        Map<String, ReportArtifactGenerator> byType = new HashMap<>();
        for (ReportArtifactGenerator generator : generators) {
            ReportArtifactGenerator previous = byType.put(generator.reportType(), generator);
            if (previous != null) {
                throw new IllegalStateException(
                        "Duplicate report artifact generator for " + generator.reportType());
            }
        }
        this.generators = Map.copyOf(byType);
    }

    @Transactional
    public StoredArtifact getOrGenerate(
            String type,
            String participant,
            String generationKey,
            int retentionDays) {
        List<Map<String, Object>> existing = find(type, participant, generationKey);
        if (!existing.isEmpty()) {
            return map(existing.getFirst());
        }

        ReportArtifactGenerator generator = Optional.ofNullable(generators.get(type))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unsupported report type " + type));
        ReportArtifact generated = generator.generate(participant, generationKey);
        if (generated.content() == null) {
            throw new IllegalStateException("Report generator returned null content");
        }
        String sha256 = sha256(generated.content());
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO report_artifact(
                    id, report_type, recipient_participant_id, generation_key,
                    content_type, file_name, content, content_sha256,
                    size_bytes, expires_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(report_type, recipient_participant_id, generation_key)
                DO NOTHING
                """,
                id,
                type,
                participant,
                generationKey,
                generated.contentType(),
                generated.fileName(),
                generated.content(),
                sha256,
                generated.content().length,
                OffsetDateTime.now().plusDays(retentionDays));

        return find(type, participant, generationKey).stream()
                .findFirst()
                .map(ReportArtifactService::map)
                .orElseThrow(() -> new IllegalStateException(
                        "Report artifact insert completed without a readable row"));
    }

    public StoredArtifact get(UUID id) {
        return jdbc.queryForObject("""
                SELECT id, file_name, content_type, content,
                       content_sha256, size_bytes
                  FROM report_artifact
                 WHERE id=?
                   AND expires_at>now()
                """,
                (resultSet, rowNumber) -> new StoredArtifact(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getString("file_name"),
                        resultSet.getString("content_type"),
                        resultSet.getBytes("content"),
                        resultSet.getString("content_sha256"),
                        resultSet.getLong("size_bytes")),
                id);
    }

    private List<Map<String, Object>> find(
            String type,
            String participant,
            String generationKey) {
        return jdbc.queryForList("""
                SELECT id, file_name, content_type, content,
                       content_sha256, size_bytes
                  FROM report_artifact
                 WHERE report_type=?
                   AND recipient_participant_id=?
                   AND generation_key=?
                """, type, participant, generationKey);
    }

    private static StoredArtifact map(Map<String, Object> row) {
        return new StoredArtifact(
                (UUID) row.get("id"),
                String.valueOf(row.get("file_name")),
                String.valueOf(row.get("content_type")),
                (byte[]) row.get("content"),
                String.valueOf(row.get("content_sha256")),
                ((Number) row.get("size_bytes")).longValue());
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(content));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    public record StoredArtifact(
            UUID id,
            String fileName,
            String contentType,
            byte[] content,
            String sha256,
            long sizeBytes) {}
}
