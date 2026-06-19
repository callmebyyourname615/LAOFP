package com.example.switching.migration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;

/**
 * Minimal process entry point used exclusively by the Kubernetes Flyway Job.
 *
 * <p>The normal {@code SwitchingApplication} must not be used for schema migration because it
 * creates schedulers, Kafka infrastructure, web endpoints, outbox workers, archive workers and
 * other runtime beans. This entry point scans only the migration package, excludes Hibernate and Kafka auto-configuration,
 * runs the expand/backfill/contract migration lifecycle, and then terminates the JVM.
 * A Flyway or datasource startup failure propagates as a non-zero process exit code.</p>
 */
@Profile("migration")
@SpringBootApplication(
        scanBasePackages = {
                "com.example.switching.migration",
                "com.example.switching.webhook.crypto"
        },
        exclude = {
                HibernateJpaAutoConfiguration.class
        },
        excludeName = {
                "org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration",
                "org.springframework.boot.task.autoconfigure.TaskSchedulingAutoConfiguration"
        })
public class MigrationApplication {

    protected MigrationApplication() {
    }

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(MigrationApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);

        ConfigurableApplicationContext context = application.run(args);
        int exitCode = SpringApplication.exit(context, () -> 0);
        System.exit(exitCode);
    }
}
