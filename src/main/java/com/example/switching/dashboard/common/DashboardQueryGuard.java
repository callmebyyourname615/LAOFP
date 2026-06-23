package com.example.switching.dashboard.common;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "switching.smos.enabled", havingValue = "true")
public class DashboardQueryGuard {
    private final JdbcTemplate jdbc;
    private final int timeoutMs;

    public DashboardQueryGuard(@Qualifier("reportingJdbcTemplate") JdbcTemplate jdbc,
            @Value("${switching.smos.dashboard-query-timeout-ms:3000}") int timeoutMs) {
        if (timeoutMs < 100 || timeoutMs > 30000) {
            throw new IllegalStateException("SMOS dashboard query timeout must be between 100 and 30000 ms");
        }
        this.jdbc = jdbc;
        this.timeoutMs = timeoutMs;
    }

    public void apply() {
        jdbc.queryForObject("SELECT set_config('statement_timeout', ?, true)",
                String.class, timeoutMs + "ms");
        jdbc.queryForObject("SELECT set_config('default_transaction_read_only', 'on', true)", String.class);
    }
}
