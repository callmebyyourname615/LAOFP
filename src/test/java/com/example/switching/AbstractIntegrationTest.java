package com.example.switching;

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

    static {
        POSTGRES = new PostgreSQLContainer<>("postgres:16")
                .withDatabaseName("switching_clean")
                .withUsername("switching_test")
                .withPassword("switching_test");
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void overrideDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.url",          POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user",         POSTGRES::getUsername);
        registry.add("spring.flyway.password",     POSTGRES::getPassword);
    }
}
