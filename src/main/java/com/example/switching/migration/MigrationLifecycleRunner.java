package com.example.switching.migration;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Orchestrates expand → encrypted data backfill → contract migrations.
 *
 * <p>This permits V43 and V44 to be delivered together without ever exposing a
 * deployment where application pods read plaintext. A Vault/KMS failure aborts
 * the Job before V44 and before the application rollout.</p>
 */
@Component
public class MigrationLifecycleRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MigrationLifecycleRunner.class);
    private static final MigrationVersion ENCRYPTION_EXPAND_VERSION =
            MigrationVersion.fromVersion("43");
    private static final MigrationVersion ENCRYPTION_CONTRACT_VERSION =
            MigrationVersion.fromVersion("44");

    private final DataSource dataSource;
    private final WebhookSecretBackfillService backfillService;

    public MigrationLifecycleRunner(DataSource dataSource,
                                    WebhookSecretBackfillService backfillService) {
        this.dataSource = dataSource;
        this.backfillService = backfillService;
    }

    @Override
    public void run(ApplicationArguments args) {
        Flyway inspector = configure().load();
        MigrationInfo current = inspector.info().current();
        MigrationVersion currentVersion = current == null ? null : current.getVersion();

        if (currentVersion == null || currentVersion.compareTo(ENCRYPTION_EXPAND_VERSION) < 0) {
            log.info("Applying database migrations through V43 before webhook secret backfill");
            configure().target(ENCRYPTION_EXPAND_VERSION).load().migrate();
            currentVersion = ENCRYPTION_EXPAND_VERSION;
        }

        if (currentVersion.compareTo(ENCRYPTION_CONTRACT_VERSION) < 0) {
            backfillService.backfill();
        }

        Flyway finalFlyway = configure().load();
        finalFlyway.migrate();
        finalFlyway.validate();
        if (finalFlyway.info().pending().length != 0) {
            throw new IllegalStateException("Flyway still reports pending migrations");
        }

        MigrationInfo finalVersion = finalFlyway.info().current();
        log.info("Successfully applied database migrations through version {}",
                finalVersion == null ? "none" : finalVersion.getVersion());
    }

    private FluentConfiguration configure() {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .validateOnMigrate(true)
                .cleanDisabled(true)
                .baselineOnMigrate(false)
                .connectRetries(3);
    }
}
