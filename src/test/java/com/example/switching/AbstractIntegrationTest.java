package com.example.switching;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for all integration tests.
 * Starts a single PostgreSQL container once for the entire test suite (singleton pattern).
 * All subclasses share the same container and Spring application context.
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES;
    private static final boolean USING_TESTCONTAINERS;

    static {
        if (isDockerSocketAvailable()) {
            USING_TESTCONTAINERS = true;
            POSTGRES = new PostgreSQLContainer<>("postgres:16")
                    .withDatabaseName("switching_clean")
                    .withUsername("switching_test")
                    .withPassword("switching_test")
                    .withInitScript("postgres-test-init.sql");
            POSTGRES.start();
            try {
                var result = POSTGRES.execInContainer(
                        "psql", "-v", "ON_ERROR_STOP=1",
                        "-U", POSTGRES.getUsername(),
                        "-d", POSTGRES.getDatabaseName(),
                        "-c", "CREATE ROLE switching_app NOLOGIN;");
                if (result.getExitCode() != 0 && !result.getStderr().contains("already exists")) {
                    throw new IllegalStateException("Unable to create switching_app test role: " + result.getStderr());
                }
            } catch (Exception exception) {
                throw new IllegalStateException("Unable to prepare PostgreSQL integration-test roles", exception);
            }
        } else {
            USING_TESTCONTAINERS = false;
            POSTGRES = null;
            prepareLocalPostgres();
        }
    }

    @DynamicPropertySource
    static void overrideDataSourceProperties(DynamicPropertyRegistry registry) {
        if (USING_TESTCONTAINERS) {
            registry.add("spring.datasource.url",      POSTGRES::getJdbcUrl);
            registry.add("spring.datasource.username", POSTGRES::getUsername);
            registry.add("spring.datasource.password", POSTGRES::getPassword);
            registry.add("spring.flyway.url",          POSTGRES::getJdbcUrl);
            registry.add("spring.flyway.user",         POSTGRES::getUsername);
            registry.add("spring.flyway.password",     POSTGRES::getPassword);
        }
    }

    private static void prepareLocalPostgres() {
        String database = localDatabaseName();
        String username = localUsername();
        String password = localPassword();
        String adminUrl = localAdminUrl();

        try (var connection = DriverManager.getConnection(adminUrl, username, password);
             var statement = connection.createStatement()) {
            connection.setAutoCommit(true);
            statement.execute("CREATE ROLE switching_app NOLOGIN");
        } catch (SQLException exception) {
            if (!exception.getMessage().contains("already exists")) {
                throw new IllegalStateException("Unable to prepare local PostgreSQL switching_app role", exception);
            }
        }

        try (var connection = DriverManager.getConnection(adminUrl, username, password);
             var statement = connection.createStatement()) {
            connection.setAutoCommit(true);
            statement.execute("DROP DATABASE IF EXISTS " + database + " WITH (FORCE)");
            statement.execute("CREATE DATABASE " + database);
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to prepare local PostgreSQL integration-test database", exception);
        }
    }

    private static String localAdminUrl() {
        return System.getenv().getOrDefault("TEST_DB_ADMIN_URL", "jdbc:postgresql://localhost:5432/postgres");
    }

    private static String localDatabaseName() {
        String url = System.getenv().getOrDefault("TEST_DB_URL", "jdbc:postgresql://localhost:5432/switching_clean");
        int slash = url.lastIndexOf('/');
        int query = url.indexOf('?', slash);
        return query > slash ? url.substring(slash + 1, query) : url.substring(slash + 1);
    }

    private static String localUsername() {
        return System.getenv().getOrDefault("TEST_DB_USERNAME", "macbookpro");
    }

    private static String localPassword() {
        return System.getenv().getOrDefault("TEST_DB_PASSWORD", "");
    }

    private static boolean isDockerSocketAvailable() {
        String dockerHost = System.getenv("DOCKER_HOST");
        if (dockerHost != null && dockerHost.startsWith("unix://")) {
            return Files.exists(Path.of(dockerHost.substring("unix://".length())));
        }
        return Files.exists(Path.of("/var/run/docker.sock"))
                || Files.exists(Path.of(System.getProperty("user.home"), ".docker", "run", "docker.sock"));
    }
}
