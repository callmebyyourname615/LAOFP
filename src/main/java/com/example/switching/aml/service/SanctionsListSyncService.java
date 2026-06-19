package com.example.switching.aml.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.example.switching.aml.sanctions.SanctionsFreshnessMonitor;
import com.example.switching.aml.sanctions.SanctionsHashing;
import com.example.switching.aml.sanctions.SanctionsImportResult;
import com.example.switching.aml.sanctions.SanctionsImportService;
import com.example.switching.aml.sanctions.SanctionsNameNormalizer;
import com.example.switching.aml.sanctions.model.SanctionsSnapshot;
import com.example.switching.aml.sanctions.provider.SanctionsProvider;

/** Scheduled orchestration for independently replaceable sanctions providers. */
@Service
@Profile("!migration")
public class SanctionsListSyncService {

    private static final Logger log = LoggerFactory.getLogger(SanctionsListSyncService.class);

    private final List<SanctionsProvider> providers;
    private final SanctionsImportService importService;
    private final SanctionsFreshnessMonitor freshnessMonitor;
    private final JdbcTemplate jdbcTemplate;

    public SanctionsListSyncService(List<SanctionsProvider> providers,
                                    SanctionsImportService importService,
                                    SanctionsFreshnessMonitor freshnessMonitor,
                                    JdbcTemplate jdbcTemplate) {
        this.providers = providers.stream()
                .sorted(Comparator.comparing(SanctionsProvider::providerCode))
                .toList();
        this.importService = importService;
        this.freshnessMonitor = freshnessMonitor;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Scheduled(cron = "${switching.aml.sanctions-sync-cron:0 0 2 * * *}", zone = "Asia/Vientiane")
    public void syncAll() {
        log.info("Sanctions synchronization started: providers={}",
                providers.stream().filter(SanctionsProvider::enabled)
                        .map(SanctionsProvider::providerCode).toList());
        for (SanctionsProvider provider : providers) {
            if (!provider.enabled()) {
                log.debug("Sanctions provider disabled: provider={}", provider.providerCode());
                continue;
            }
            try {
                sync(provider);
            } catch (RuntimeException error) {
                // Last-known-good data remains active because staging never reached the atomic swap.
                log.error("Sanctions provider synchronization failed; retaining last-known-good data: provider={} error={}",
                        provider.providerCode(), error.getMessage(), error);
            }
        }
        freshnessMonitor.refresh();
        log.info("Sanctions synchronization completed");
    }

    public SanctionsImportResult syncProvider(String providerCode) {
        SanctionsProvider provider = providers.stream()
                .filter(candidate -> candidate.providerCode().equalsIgnoreCase(providerCode))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown sanctions provider: " + providerCode));
        if (!provider.enabled()) {
            throw new IllegalStateException("Sanctions provider is disabled: " + provider.providerCode());
        }
        SanctionsImportResult result = sync(provider);
        freshnessMonitor.refresh();
        return result;
    }

    private SanctionsImportResult sync(SanctionsProvider provider) {
        SanctionsSnapshot snapshot = provider.fetchSnapshot();
        SanctionsImportResult result = importService.importSnapshot(snapshot, provider.minimumRecords());
        log.info("Sanctions provider synchronized: provider={} sourceRef={} records={} runId={}",
                provider.providerCode(), snapshot.sourceReference(), snapshot.entries().size(), result.runId());
        return result;
    }

    /** Test/local helper. Production imports must use a provider snapshot. */
    public void seedTestEntry(String entityName, String listType, String entityType, String sourceRef) {
        String normalized = SanctionsNameNormalizer.normalize(entityName);
        String uid = listType + ":TEST:" + SanctionsHashing.sha256(
                (sourceRef + ':' + entityName).getBytes(StandardCharsets.UTF_8)).substring(0, 20);
        String hash = SanctionsHashing.sha256(
                (uid + ':' + normalized + ':' + entityType).getBytes(StandardCharsets.UTF_8));
        jdbcTemplate.update("""
                INSERT INTO sanctions_lists
                    (list_type, provider_uid, entity_name, normalized_name, entity_type,
                     aliases, aliases_normalized, identifiers, source_ref, content_hash,
                     is_active, first_seen_at, last_seen_at, updated_at)
                VALUES (?, ?, ?, ?, ?, '[]'::jsonb, '[]'::jsonb, '{}'::jsonb, ?, ?,
                        TRUE, ?, ?, ?)
                ON CONFLICT (list_type, provider_uid) DO UPDATE SET
                    entity_name = EXCLUDED.entity_name,
                    normalized_name = EXCLUDED.normalized_name,
                    is_active = TRUE,
                    last_seen_at = EXCLUDED.last_seen_at,
                    updated_at = EXCLUDED.updated_at
                """, listType, uid, entityName, normalized, entityType, sourceRef, hash,
                Instant.now(), Instant.now(), Instant.now());
    }
}
