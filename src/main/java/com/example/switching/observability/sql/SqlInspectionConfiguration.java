package com.example.switching.observability.sql;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import jakarta.annotation.PostConstruct;

@Configuration
public class SqlInspectionConfiguration {
    private final boolean enabled;
    private final int queryThreshold;
    private final int repeatedThreshold;

    public SqlInspectionConfiguration(
            @Value("${switching.sql-inspection.enabled:false}") boolean enabled,
            @Value("${switching.sql-inspection.query-warning-threshold:25}") int queryThreshold,
            @Value("${switching.sql-inspection.repeated-statement-threshold:5}") int repeatedThreshold) {
        this.enabled = enabled;
        this.queryThreshold = queryThreshold;
        this.repeatedThreshold = repeatedThreshold;
    }

    @PostConstruct
    void configure() {
        if (queryThreshold < 1 || repeatedThreshold < 2) {
            throw new IllegalStateException("Invalid SQL inspection thresholds");
        }
        NPlusOneStatementInspector.configure(enabled, queryThreshold, repeatedThreshold);
    }
}
