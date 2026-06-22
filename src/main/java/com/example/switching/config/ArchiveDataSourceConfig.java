package com.example.switching.config;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@Configuration
@EnableConfigurationProperties(ReadReplicaProperties.class)
public class ArchiveDataSourceConfig {
    @Bean("writeDataSource")
    @ConfigurationProperties("spring.datasource.hikari")
    public DataSource writeDataSource(DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }

    @Bean("readDataSource")
    public DataSource readDataSource(@Qualifier("writeDataSource") DataSource writeDataSource,
                                     ReadReplicaProperties properties) {
        if (!properties.isConfigured()) return writeDataSource;
        HikariDataSource readDataSource = new HikariDataSource();
        readDataSource.setDriverClassName("org.postgresql.Driver");
        readDataSource.setJdbcUrl(properties.getUrl());
        readDataSource.setUsername(properties.getUsername());
        readDataSource.setPassword(properties.getPassword());
        readDataSource.setReadOnly(true);
        readDataSource.setPoolName("SwitchingReadReplicaPool");
        return readDataSource;
    }

    @Bean
    @Primary
    public DataSource dataSource(@Qualifier("writeDataSource") DataSource writeDataSource) {
        // The primary is deliberately the default for every JPA/JDBC call.  A
        // read-only transaction can still be correctness-sensitive in payment flows.
        return writeDataSource;
    }

    /**
     * Explicitly declare the primary JdbcTemplate so that Spring Boot's
     * {@code @ConditionalOnMissingBean(JdbcOperations.class)} check — which
     * would be triggered by the presence of {@code archiveJdbcTemplate} —
     * does not suppress it.
     *
     * All injection points that use {@code @Autowired JdbcTemplate jdbcTemplate}
     * (without a qualifier) receive this bean.
     */
    @Bean
    @Primary
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    /** Opt-in template for dashboards and historical reports that tolerate replica lag. */
    @Bean
    @Qualifier("reportingJdbcTemplate")
    public JdbcTemplate reportingJdbcTemplate(@Qualifier("readDataSource") DataSource readDataSource) {
        return new JdbcTemplate(readDataSource);
    }

    /**
     * Secondary JdbcTemplate wired to the WARM archive database.
     * Inject with {@code @Qualifier("archiveJdbcTemplate")} wherever archive
     * DB writes are needed (e.g. {@link com.example.switching.maintenance.service.ArchiveWorkerService}).
     */
    @Bean
    @Qualifier("archiveJdbcTemplate")
    public JdbcTemplate archiveJdbcTemplate(ArchiveProperties properties) {
        DriverManagerDataSource archiveDataSource = new DriverManagerDataSource();
        archiveDataSource.setDriverClassName("org.postgresql.Driver");
        archiveDataSource.setUrl(properties.getArchiveDbUrl());
        archiveDataSource.setUsername(properties.getArchiveDbUsername());
        archiveDataSource.setPassword(properties.getArchiveDbPassword());
        return new JdbcTemplate(archiveDataSource);
    }
}
