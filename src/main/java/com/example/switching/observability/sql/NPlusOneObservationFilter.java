package com.example.switching.observability.sql;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@Order(Ordered.LOWEST_PRECEDENCE - 20)
@ConditionalOnProperty(name = "switching.sql-inspection.enabled", havingValue = "true")
public class NPlusOneObservationFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(NPlusOneObservationFilter.class);
    private final Counter queryBudgetWarnings;
    private final Counter repeatedStatementWarnings;

    public NPlusOneObservationFilter(MeterRegistry registry) {
        this.queryBudgetWarnings = Counter.builder("switching.sql.query_budget.warnings")
                .description("Requests exceeding the approved SQL query budget").register(registry);
        this.repeatedStatementWarnings = Counter.builder("switching.sql.repeated_statement.warnings")
                .description("Requests showing a possible N+1 repeated SQL fingerprint").register(registry);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        NPlusOneStatementInspector.beginRequest();
        try {
            filterChain.doFilter(request, response);
        } finally {
            var snapshot = NPlusOneStatementInspector.endRequest();
            if (snapshot.totalQueries() > NPlusOneStatementInspector.queryWarningThreshold()) {
                queryBudgetWarnings.increment();
                log.warn("SQL query budget exceeded method={} path={} queryCount={}",
                        request.getMethod(), request.getRequestURI(), snapshot.totalQueries());
            }
            if (snapshot.maxRepeatedCount() >= NPlusOneStatementInspector.repeatedStatementThreshold()) {
                repeatedStatementWarnings.increment();
                log.warn("Possible N+1 query pattern method={} path={} repeatedCount={} fingerprint={}",
                        request.getMethod(), request.getRequestURI(), snapshot.maxRepeatedCount(),
                        snapshot.mostRepeatedFingerprint());
            }
        }
    }
}
