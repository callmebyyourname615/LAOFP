package com.example.switching.operations.service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.example.switching.operations.dto.OperationsHealthResponse;

@Service
public class OperationsHealthService {

    private final JdbcTemplate jdbcTemplate;

    public OperationsHealthService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public OperationsHealthResponse getHealth() {
        boolean databaseUp = isDatabaseUp();

        Map<String, Object> database = new LinkedHashMap<>();
        database.put("status", databaseUp ? "UP" : "DOWN");
        database.put("reachable", databaseUp);

        Map<String, Long> transfers = new LinkedHashMap<>();
        transfers.put("total", safeCount("SELECT COUNT(*) FROM transactions"));
        transfers.put("received", safeCount("SELECT COUNT(*) FROM transactions WHERE status IN ('ACCEPTED', 'RECEIVED')"));
        transfers.put("success", safeCount("SELECT COUNT(*) FROM transactions WHERE status IN ('SETTLED', 'SUCCESS')"));
        transfers.put("failed", safeCount("SELECT COUNT(*) FROM transactions WHERE status IN ('REJECTED', 'FAILED')"));

        Map<String, Long> outbox = new LinkedHashMap<>();
        outbox.put("total", safeCount("SELECT COUNT(*) FROM outbox_messages"));
        outbox.put("pending", safeCount("SELECT COUNT(*) FROM outbox_messages WHERE status = 'PENDING'"));
        outbox.put("processing", safeCount("SELECT COUNT(*) FROM outbox_messages WHERE status = 'PROCESSING'"));
        outbox.put("success", safeCount("SELECT COUNT(*) FROM outbox_messages WHERE status = 'SUCCESS'"));
        outbox.put("failed", safeCount("SELECT COUNT(*) FROM outbox_messages WHERE status = 'FAILED'"));

        Map<String, Long> participants = new LinkedHashMap<>();
        participants.put("total", safeCount("SELECT COUNT(*) FROM participants"));
        participants.put("active", safeCount("SELECT COUNT(*) FROM participants WHERE status = 'ACTIVE'"));
        participants.put("inactive", safeCount("SELECT COUNT(*) FROM participants WHERE status = 'INACTIVE'"));
        participants.put("maintenance", safeCount("SELECT COUNT(*) FROM participants WHERE status = 'MAINTENANCE'"));

        Map<String, Long> connectors = new LinkedHashMap<>();
        connectors.put("total", safeCount("SELECT COUNT(*) FROM connector_configs"));
        connectors.put("enabled", safeCount("SELECT COUNT(*) FROM connector_configs WHERE enabled = true"));
        connectors.put("disabled", safeCount("SELECT COUNT(*) FROM connector_configs WHERE enabled = false"));
        connectors.put("forceReject", safeCount("SELECT COUNT(*) FROM connector_configs WHERE force_reject = true"));

        Map<String, Long> routingRules = new LinkedHashMap<>();
        routingRules.put("total", safeCount("SELECT COUNT(*) FROM routing_rules"));
        routingRules.put("enabled", safeCount("SELECT COUNT(*) FROM routing_rules WHERE enabled = true"));
        routingRules.put("disabled", safeCount("SELECT COUNT(*) FROM routing_rules WHERE enabled = false"));

        String status = resolveOverallStatus(databaseUp, transfers, outbox, participants, connectors, routingRules);
        String message = resolveMessage(status, outbox);

        return new OperationsHealthResponse(
                status,
                LocalDateTime.now(),
                database,
                transfers,
                outbox,
                participants,
                connectors,
                routingRules,
                message
        );
    }

    private boolean isDatabaseUp() {
        try {
            Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return result != null && result == 1;
        } catch (Exception ex) {
            return false;
        }
    }

    private Long safeCount(String sql) {
        try {
            Long result = jdbcTemplate.queryForObject(sql, Long.class);
            return result == null ? 0L : result;
        } catch (Exception ex) {
            /*
             * Return -1 instead of failing the health API.
             * This makes the endpoint useful even while schemas evolve.
             */
            return -1L;
        }
    }

    private String resolveOverallStatus(
            boolean databaseUp,
            Map<String, Long> transfers,
            Map<String, Long> outbox,
            Map<String, Long> participants,
            Map<String, Long> connectors,
            Map<String, Long> routingRules
    ) {
        if (!databaseUp) {
            return "DOWN";
        }

        if (hasUnknownCount(transfers)
                || hasUnknownCount(outbox)
                || hasUnknownCount(participants)
                || hasUnknownCount(connectors)
                || hasUnknownCount(routingRules)) {
            return "DEGRADED";
        }

        long outboxFailed = outbox.getOrDefault("failed", 0L);
        long outboxProcessing = outbox.getOrDefault("processing", 0L);
        long activeParticipants = participants.getOrDefault("active", 0L);
        long enabledConnectors = connectors.getOrDefault("enabled", 0L);
        long enabledRoutes = routingRules.getOrDefault("enabled", 0L);

        if (activeParticipants == 0 || enabledConnectors == 0 || enabledRoutes == 0) {
            return "DEGRADED";
        }

        if (outboxFailed > 0) {
            return "DEGRADED";
        }

        /*
         * PROCESSING is not always bad, but if there are processing events,
         * operators should keep an eye on the system.
         */
        if (outboxProcessing > 0) {
            return "DEGRADED";
        }

        return "HEALTHY";
    }

    private boolean hasUnknownCount(Map<String, Long> counts) {
        return counts.values().stream().anyMatch(value -> value != null && value < 0);
    }

    private String resolveMessage(String status, Map<String, Long> outbox) {
        if ("DOWN".equals(status)) {
            return "Switching database is not reachable";
        }

        if ("DEGRADED".equals(status)) {
            long failed = outbox.getOrDefault("failed", 0L);
            long processing = outbox.getOrDefault("processing", 0L);

            if (failed > 0) {
                return "Switching is running but there are failed outbox events";
            }

            if (processing > 0) {
                return "Switching is running but there are processing outbox events";
            }

            return "Switching is running but some operational checks need attention";
        }

        return "Switching is healthy";
    }
}
