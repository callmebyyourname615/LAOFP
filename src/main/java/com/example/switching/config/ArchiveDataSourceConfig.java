package com.example.switching.config;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@Configuration
public class ArchiveDataSourceConfig {
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
