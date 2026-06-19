package com.example.switching.aml.sanctions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.example.switching.AbstractIntegrationTest;
import com.example.switching.aml.sanctions.model.SanctionsEntityType;
import com.example.switching.aml.sanctions.model.SanctionsEntry;
import com.example.switching.aml.sanctions.model.SanctionsSnapshot;
import com.example.switching.aml.sanctions.provider.SanctionsProviderException;

class SanctionsImportIntegrationTest extends AbstractIntegrationTest {

    @Autowired SanctionsImportService importService;
    @Autowired JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("DELETE FROM sanctions_import_staging WHERE provider_code = 'OFAC'");
        jdbcTemplate.update("DELETE FROM sanctions_import_runs WHERE provider_code = 'OFAC'");
        jdbcTemplate.update("DELETE FROM sanctions_lists WHERE provider_uid LIKE 'OFAC:IT-%'");
    }

    @Test
    void stagesUpsertsAndSoftDeletesInOneSnapshotSwap() {
        var first = snapshot("first", List.of(
                entry("OFAC:IT-1", "JOSE ALVAREZ", List.of("JOSE A")),
                entry("OFAC:IT-2", "EXAMPLE COMPANY", List.of())));
        SanctionsImportResult initial = importService.importSnapshot(first, 1);
        assertEquals(2, initial.inserted());
        assertEquals(0, initial.deactivated());

        var second = snapshot("second", List.of(
                entry("OFAC:IT-1", "JOSÉ ÁLVAREZ", List.of("JOSE A", "J ALVAREZ"))));
        SanctionsImportResult replacement = importService.importSnapshot(second, 1);
        assertEquals(0, replacement.inserted());
        assertEquals(1, replacement.updated());
        assertEquals(1, replacement.deactivated());

        Map<String, Object> active = jdbcTemplate.queryForMap("""
                SELECT entity_name, normalized_name, aliases_normalized, is_active
                  FROM sanctions_lists WHERE provider_uid = 'OFAC:IT-1'
                """);
        assertEquals("JOSÉ ÁLVAREZ", active.get("entity_name"));
        assertEquals("jose alvarez", active.get("normalized_name"));
        assertEquals(Boolean.TRUE, active.get("is_active"));
        assertEquals(Boolean.FALSE, jdbcTemplate.queryForObject(
                "SELECT is_active FROM sanctions_lists WHERE provider_uid = 'OFAC:IT-2'", Boolean.class));
    }

    @Test
    void rejectsTooSmallSnapshotWithoutDeactivatingLastKnownGood() {
        importService.importSnapshot(snapshot("good", List.of(entry("OFAC:IT-1", "SAFE BASELINE", List.of()))), 1);

        assertThrows(SanctionsProviderException.class,
                () -> importService.importSnapshot(snapshot("empty", List.of()), 1));

        assertTrue(Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT is_active FROM sanctions_lists WHERE provider_uid = 'OFAC:IT-1'", Boolean.class)));
        assertEquals("REJECTED", jdbcTemplate.queryForObject("""
                SELECT status FROM sanctions_import_runs
                 WHERE provider_code = 'OFAC'
                 ORDER BY started_at DESC LIMIT 1
                """, String.class));
    }

    private SanctionsEntry entry(String uid, String name, List<String> aliases) {
        return new SanctionsEntry(uid, name, aliases, SanctionsEntityType.PERSON,
                Map.of("test", true), "IT");
    }

    private SanctionsSnapshot snapshot(String marker, List<SanctionsEntry> entries) {
        byte[] content = marker.getBytes(StandardCharsets.UTF_8);
        return new SanctionsSnapshot("OFAC", "OFAC-IT-" + marker, Instant.now(),
                SanctionsHashing.sha256(content), entries);
    }
}
