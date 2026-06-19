package com.example.switching.aml.sanctions;

import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.switching.aml.sanctions.model.SanctionsEntry;
import com.example.switching.aml.sanctions.model.SanctionsSnapshot;
import com.example.switching.aml.sanctions.provider.SanctionsProviderException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.micrometer.core.instrument.MeterRegistry;

/** Stages a complete provider snapshot and atomically swaps it into the live list. */
@Service
public class SanctionsImportService {

    private static final Logger log = LoggerFactory.getLogger(SanctionsImportService.class);

    private static final String INSERT_RUN_SQL = """
            INSERT INTO sanctions_import_runs
                (run_id, provider_code, source_ref, content_sha256, status, fetched_at, started_at, parsed_count)
            VALUES (?, ?, ?, ?, 'STARTED', ?, NOW(), ?)
            """;

    private static final String INSERT_STAGING_SQL = """
            INSERT INTO sanctions_import_staging
                (batch_id, provider_code, provider_uid, entity_name, normalized_name,
                 entity_type, aliases, aliases_normalized, identifiers, source_ref, content_hash)
            VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?, ?)
            """;

    private static final String UPSERT_SQL = """
            INSERT INTO sanctions_lists
                (list_type, provider_uid, entity_name, normalized_name, entity_type,
                 aliases, aliases_normalized, identifiers, source_ref, content_hash,
                 is_active, first_seen_at, last_seen_at, updated_at)
            SELECT provider_code, provider_uid, entity_name, normalized_name, entity_type,
                   aliases, aliases_normalized, identifiers, source_ref, content_hash,
                   TRUE, NOW(), NOW(), NOW()
              FROM sanctions_import_staging
             WHERE batch_id = ? AND provider_code = ?
            ON CONFLICT (list_type, provider_uid) DO UPDATE SET
                entity_name = EXCLUDED.entity_name,
                normalized_name = EXCLUDED.normalized_name,
                entity_type = EXCLUDED.entity_type,
                aliases = EXCLUDED.aliases,
                aliases_normalized = EXCLUDED.aliases_normalized,
                identifiers = EXCLUDED.identifiers,
                source_ref = EXCLUDED.source_ref,
                content_hash = EXCLUDED.content_hash,
                is_active = TRUE,
                last_seen_at = NOW(),
                updated_at = CASE
                    WHEN sanctions_lists.content_hash IS DISTINCT FROM EXCLUDED.content_hash
                    THEN NOW() ELSE sanctions_lists.updated_at END
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final MeterRegistry meterRegistry;

    public SanctionsImportService(JdbcTemplate jdbcTemplate,
                                  ObjectMapper objectMapper,
                                  PlatformTransactionManager transactionManager,
                                  MeterRegistry meterRegistry) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.meterRegistry = meterRegistry;
    }

    public SanctionsImportResult importSnapshot(SanctionsSnapshot snapshot, int minimumRecords) {
        try {
            validateSnapshot(snapshot, minimumRecords);
        } catch (RuntimeException validationError) {
            recordRejected(snapshot.providerCode(), snapshot.sourceReference(), snapshot.contentSha256(),
                    snapshot.entries().size(), validationError.getMessage());
            throw validationError;
        }
        UUID runId = UUID.randomUUID();
        jdbcTemplate.update(INSERT_RUN_SQL,
                runId, snapshot.providerCode(), snapshot.sourceReference(), snapshot.contentSha256(),
                Timestamp.from(snapshot.fetchedAt()), snapshot.entries().size());

        try {
            SanctionsImportResult result = transactionTemplate.execute(status -> importInTransaction(runId, snapshot));
            if (result == null) {
                throw new SanctionsProviderException("Sanctions import transaction returned no result");
            }
            meterRegistry.counter("switching.aml.sanctions.imports", "provider", snapshot.providerCode(),
                    "status", "success").increment();
            meterRegistry.summary("switching.aml.sanctions.records", "provider", snapshot.providerCode())
                    .record(snapshot.entries().size());
            return result;
        } catch (RuntimeException error) {
            recordFailure(runId, error);
            meterRegistry.counter("switching.aml.sanctions.imports", "provider", snapshot.providerCode(),
                    "status", "failed").increment();
            throw error;
        }
    }

    public void recordRejected(String providerCode, String sourceRef, String contentHash,
                               int parsedCount, String reason) {
        UUID runId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO sanctions_import_runs
                    (run_id, provider_code, source_ref, content_sha256, status,
                     started_at, completed_at, parsed_count, error_code, error_message)
                VALUES (?, ?, ?, ?, 'REJECTED', NOW(), NOW(), ?, 'SNAPSHOT_REJECTED', ?)
                """, runId, providerCode, sourceRef, contentHash, parsedCount, truncate(reason));
        meterRegistry.counter("switching.aml.sanctions.imports", "provider", providerCode,
                "status", "rejected").increment();
    }

    private SanctionsImportResult importInTransaction(UUID runId, SanctionsSnapshot snapshot) {
        // Serialize imports per provider so overlapping schedules/manual runs cannot deactivate
        // records from a newer snapshot. The lock is released automatically at transaction end.
        jdbcTemplate.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT pg_advisory_xact_lock(hashtextextended(?, 0))")) {
                statement.setString(1, "sanctions-import:" + snapshot.providerCode());
                statement.execute();
            }
            return null;
        });
        stage(runId, snapshot);
        Integer existing = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                  FROM sanctions_lists live
                  JOIN sanctions_import_staging staged
                    ON staged.provider_code = live.list_type
                   AND staged.provider_uid = live.provider_uid
                 WHERE staged.batch_id = ? AND staged.provider_code = ?
                """, Integer.class, runId, snapshot.providerCode());
        int updated = existing == null ? 0 : existing;
        int inserted = snapshot.entries().size() - updated;

        jdbcTemplate.update(UPSERT_SQL, runId, snapshot.providerCode());
        int deactivated = jdbcTemplate.update("""
                UPDATE sanctions_lists live
                   SET is_active = FALSE,
                       updated_at = NOW()
                 WHERE live.list_type = ?
                   AND live.is_active = TRUE
                   AND NOT EXISTS (
                       SELECT 1
                         FROM sanctions_import_staging staged
                        WHERE staged.batch_id = ?
                          AND staged.provider_code = live.list_type
                          AND staged.provider_uid = live.provider_uid
                   )
                """, snapshot.providerCode(), runId);

        jdbcTemplate.update("DELETE FROM sanctions_import_staging WHERE batch_id = ?", runId);
        jdbcTemplate.update("""
                UPDATE sanctions_import_runs
                   SET status = 'SUCCESS', completed_at = NOW(),
                       inserted_count = ?, updated_count = ?, deactivated_count = ?
                 WHERE run_id = ?
                """, inserted, updated, deactivated, runId);

        log.info("Sanctions import completed: provider={} parsed={} inserted={} updated={} deactivated={} runId={}",
                snapshot.providerCode(), snapshot.entries().size(), inserted, updated, deactivated, runId);
        return new SanctionsImportResult(
                runId, snapshot.providerCode(), snapshot.entries().size(), inserted, updated, deactivated);
    }

    private void stage(UUID batchId, SanctionsSnapshot snapshot) {
        jdbcTemplate.batchUpdate(INSERT_STAGING_SQL, snapshot.entries(), 500,
                (PreparedStatement statement, SanctionsEntry entry) -> {
                    List<String> normalizedAliases = entry.aliases().stream()
                            .map(SanctionsNameNormalizer::normalize)
                            .filter(value -> !value.isBlank())
                            .distinct()
                            .toList();
                    String contentHash = entryHash(entry, normalizedAliases);
                    statement.setObject(1, batchId);
                    statement.setString(2, snapshot.providerCode());
                    statement.setString(3, entry.providerUid());
                    statement.setString(4, entry.primaryName());
                    statement.setString(5, SanctionsNameNormalizer.normalize(entry.primaryName()));
                    statement.setString(6, entry.entityType().name());
                    statement.setString(7, json(entry.aliases()));
                    statement.setString(8, json(normalizedAliases));
                    statement.setString(9, json(entry.identifiers()));
                    statement.setString(10, entry.sourceReference().isBlank()
                            ? snapshot.sourceReference() : entry.sourceReference());
                    statement.setString(11, contentHash);
                });
    }

    private String entryHash(SanctionsEntry entry, List<String> normalizedAliases) {
        String canonical = entry.providerUid() + '\n'
                + SanctionsNameNormalizer.normalize(entry.primaryName()) + '\n'
                + entry.entityType().name() + '\n'
                + json(normalizedAliases) + '\n'
                + json(entry.identifiers());
        return SanctionsHashing.sha256(canonical.getBytes(StandardCharsets.UTF_8));
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException error) {
            throw new SanctionsProviderException("Unable to serialize sanctions entry", error);
        }
    }

    private void validateSnapshot(SanctionsSnapshot snapshot, int minimumRecords) {
        if (snapshot.entries().size() < Math.max(1, minimumRecords)) {
            throw new SanctionsProviderException("Provider " + snapshot.providerCode()
                    + " returned only " + snapshot.entries().size()
                    + " records; minimum is " + minimumRecords + ". Last-known-good data retained.");
        }
        Set<String> uids = new HashSet<>();
        for (SanctionsEntry entry : snapshot.entries()) {
            if (!entry.providerUid().startsWith(snapshot.providerCode() + ":")) {
                throw new SanctionsProviderException("Provider UID does not match snapshot provider: "
                        + entry.providerUid());
            }
            if (!uids.add(entry.providerUid())) {
                throw new SanctionsProviderException("Duplicate provider UID in snapshot: "
                        + entry.providerUid());
            }
            if (SanctionsNameNormalizer.normalize(entry.primaryName()).isBlank()) {
                throw new SanctionsProviderException("Entry normalizes to an empty name: "
                        + entry.providerUid());
            }
        }
    }

    private void recordFailure(UUID runId, RuntimeException error) {
        try {
            jdbcTemplate.update("""
                    UPDATE sanctions_import_runs
                       SET status = 'FAILED', completed_at = NOW(),
                           error_code = ?, error_message = ?
                     WHERE run_id = ?
                    """, error.getClass().getSimpleName(), truncate(error.getMessage()), runId);
        } catch (RuntimeException auditError) {
            log.error("Unable to persist sanctions import failure: runId={}", runId, auditError);
        }
    }

    private String truncate(String value) {
        if (value == null) {
            return "unspecified error";
        }
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }
}
