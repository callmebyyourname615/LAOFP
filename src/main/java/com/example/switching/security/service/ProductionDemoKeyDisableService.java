package com.example.switching.security.service;

import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.switching.security.entity.ApiKeyEntity;
import com.example.switching.security.repository.ApiKeyRepository;

/**
 * On prod startup, disables well-known demo API keys seeded by V14 migration.
 * Identified by both name AND key_prefix to avoid accidental disabling of
 * legitimately named keys that aren't the demo ones.
 */
@Profile("prod")
@Component
public class ProductionDemoKeyDisableService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ProductionDemoKeyDisableService.class);

    // Matches V14 seed names exactly
    private static final Set<String> DEMO_KEY_NAMES = Set.of(
            "Admin Key", "Operations Key", "Bank A Key", "Bank B Key"
    );

    // First 12 chars of the V14 demo plaintexts — used as second check
    private static final Set<String> DEMO_KEY_PREFIXES = Set.of(
            "sk-admin-swi",   // sk-admin-switching-2026
            "sk-ops-switc",   // sk-ops-switching-2026
            "sk-bank-a-sw",   // sk-bank-a-switching-2026
            "sk-bank-b-sw"    // sk-bank-b-switching-2026
    );

    private final ApiKeyRepository apiKeyRepository;

    public ProductionDemoKeyDisableService(ApiKeyRepository apiKeyRepository) {
        this.apiKeyRepository = apiKeyRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<ApiKeyEntity> toDisable = apiKeyRepository.findAll().stream()
                .filter(ApiKeyEntity::isEnabled)
                .filter(k -> DEMO_KEY_NAMES.contains(k.getName())
                          && DEMO_KEY_PREFIXES.contains(k.getKeyPrefix()))
                .toList();

        if (toDisable.isEmpty()) {
            log.info("[PROD-SECURITY] No active demo API keys found — nothing to disable.");
            return;
        }

        toDisable.forEach(k -> k.setEnabled(false));
        apiKeyRepository.saveAll(toDisable);

        log.warn("[PROD-SECURITY] Disabled {} demo API key(s): {}. "
                + "Provision a real ADMIN key via POST /api/admin/api-keys before use.",
                toDisable.size(),
                toDisable.stream().map(k -> k.getName() + " (" + k.getKeyPrefix() + "...)").toList());
    }
}
