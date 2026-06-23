package com.example.switching.config;

import java.util.Map;
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
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;

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
        properties.validate();
        HikariDataSource replica = new HikariDataSource();
        replica.setDriverClassName("org.postgresql.Driver");
        replica.setJdbcUrl(properties.getUrl());
        replica.setUsername(properties.getUsername());
        replica.setPassword(properties.getPassword());
        replica.setReadOnly(true);
        replica.setPoolName("SwitchingReadReplicaPool");
        replica.setMaximumPoolSize(properties.getMaximumPoolSize());
        replica.setMinimumIdle(properties.getMinimumIdle());
        replica.setConnectionTimeout(properties.getConnectionTimeoutMs());
        replica.setValidationTimeout(properties.getValidationTimeoutMs());
        replica.setAutoCommit(false);
        return replica;
    }

    @Bean("routingDataSource")
    public TransactionRoutingDataSource routingDataSource(
            @Qualifier("writeDataSource") DataSource writeDataSource,
            @Qualifier("readDataSource") DataSource readDataSource) {
        TransactionRoutingDataSource routing = new TransactionRoutingDataSource();
        routing.setTargetDataSources(Map.of(
                DataSourceRoute.PRIMARY, writeDataSource,
                DataSourceRoute.REPLICA, readDataSource));
        routing.setDefaultTargetDataSource(writeDataSource);
        routing.setLenientFallback(false);
        routing.afterPropertiesSet();
        return routing;
    }

    @Bean
    @Primary
    public DataSource dataSource(@Qualifier("routingDataSource") DataSource routingDataSource) {
        // Lazy acquisition is mandatory: Spring marks the transaction read-only before
        // the physical connection is requested, allowing the router to choose correctly.
        return new LazyConnectionDataSourceProxy(routingDataSource);
    }

    @Bean
    @Primary
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    /** Explicit replica template for dashboards/reports that tolerate replication lag. */
    @Bean
    @Qualifier("reportingJdbcTemplate")
    public JdbcTemplate reportingJdbcTemplate(@Qualifier("readDataSource") DataSource readDataSource) {
        return new JdbcTemplate(readDataSource);
    }

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
