package com.example.switching.aml.sanctions;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.example.switching.AbstractIntegrationTest;
import com.example.switching.aml.exception.SanctionsBlockException;
import com.example.switching.aml.sanctions.model.SanctionsEntityType;
import com.example.switching.aml.sanctions.model.SanctionsEntry;
import com.example.switching.aml.sanctions.model.SanctionsSnapshot;
import com.example.switching.aml.service.SanctionsScreeningService;

class SanctionsAliasScreeningIntegrationTest extends AbstractIntegrationTest {

    @Autowired SanctionsImportService importService;
    @Autowired SanctionsScreeningService screeningService;
    @Autowired JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("DELETE FROM sanctions_screening_results WHERE txn_id = 'TC-AML-ALIAS'");
        jdbcTemplate.update("DELETE FROM str_reports WHERE txn_id = 'TC-AML-ALIAS'");
        jdbcTemplate.update("DELETE FROM sanctions_import_runs WHERE provider_code = 'UN'");
        jdbcTemplate.update("DELETE FROM sanctions_lists WHERE provider_uid = 'UN:IT-ALIAS'");
    }

    @Test
    void normalizedStrongAliasBlocks() {
        SanctionsEntry entry = new SanctionsEntry(
                "UN:IT-ALIAS", "PRIMARY SUBJECT", List.of("José Álvarez"),
                SanctionsEntityType.PERSON, Map.of(), "UN-IT");
        importService.importSnapshot(new SanctionsSnapshot(
                "UN", "UN-IT", Instant.now(),
                SanctionsHashing.sha256("alias".getBytes(StandardCharsets.UTF_8)), List.of(entry)), 1);

        assertThrows(SanctionsBlockException.class,
                () -> screeningService.screen("TC-AML-ALIAS", "JOSE, ALVAREZ", "CLEAN NAME"));
    }
}
