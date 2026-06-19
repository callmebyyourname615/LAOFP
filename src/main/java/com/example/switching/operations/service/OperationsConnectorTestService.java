package com.example.switching.operations.service;

import com.example.switching.operations.dto.OperationsConnectorTestResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.ResultSet;
import java.time.Duration;
import java.time.LocalDateTime;

@Service
public class OperationsConnectorTestService {

    private final JdbcTemplate jdbcTemplate;

    public OperationsConnectorTestService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public OperationsConnectorTestResponse testConnector(String connectorName) {
        long started = System.currentTimeMillis();

        ConnectorConfigRow config = findConnector(connectorName);

        if (!config.enabled()) {
            return buildResponse(
                    "DISABLED",
                    config,
                    false,
                    "CONNECTOR_DISABLED",
                    "Connector is disabled",
                    started
            );
        }

        if (config.forceReject()) {
            return buildResponse(
                    "FORCE_REJECT_ENABLED",
                    config,
                    true,
                    config.rejectReasonCode() == null ? "FORCE_REJECT" : config.rejectReasonCode(),
                    config.rejectReasonMessage() == null ? "Connector is configured to force reject" : config.rejectReasonMessage(),
                    started
            );
        }

        if ("MOCK".equalsIgnoreCase(config.connectorType())) {
            return buildResponse(
                    "TEST_PASSED",
                    config,
                    true,
                    "00",
                    "Mock connector is reachable and operational",
                    started
            );
        }

        if ("HTTP".equalsIgnoreCase(config.connectorType())) {
            if (!StringUtils.hasText(config.endpointUrl())) {
                return buildResponse(
                        "CONFIG_ERROR",
                        config,
                        false,
                        "ENDPOINT_MISSING",
                        "HTTP connector endpointUrl is missing",
                        started
                );
            }

            return buildResponse(
                    "CONFIGURED",
                    config,
                    true,
                    "HTTP_CONFIGURED",
                    "HTTP connector is configured. Network health check can be added later.",
                    started
            );
        }

        if ("MQ".equalsIgnoreCase(config.connectorType())) {
            return buildResponse(
                    "CONFIGURED",
                    config,
                    true,
                    "MQ_CONFIGURED",
                    "MQ connector is configured. Broker health check can be added later.",
                    started
            );
        }

        return buildResponse(
                "UNKNOWN_CONNECTOR_TYPE",
                config,
                false,
                "UNKNOWN_CONNECTOR_TYPE",
                "Unsupported connectorType=" + config.connectorType(),
                started
        );
    }

    private ConnectorConfigRow findConnector(String connectorName) {
        if (!StringUtils.hasText(connectorName)) {
            throw new IllegalArgumentException("connectorName is required");
        }

        return jdbcTemplate.query(
                """
                SELECT
                    connector_name,
                    bank_code,
                    connector_type,
                    endpoint_url,
                    timeout_ms,
                    enabled,
                    force_reject,
                    reject_reason_code,
                    reject_reason_message
                FROM connector_configs
                WHERE connector_name = ?
                """,
                rs -> {
                    if (!rs.next()) {
                        throw new IllegalArgumentException("Connector config not found: " + connectorName);
                    }

                    return mapRow(rs);
                },
                connectorName.trim().toUpperCase()
        );
    }

    private ConnectorConfigRow mapRow(ResultSet rs) throws java.sql.SQLException {
        return new ConnectorConfigRow(
                rs.getString("connector_name"),
                rs.getString("bank_code"),
                rs.getString("connector_type"),
                rs.getString("endpoint_url"),
                rs.getInt("timeout_ms"),
                rs.getBoolean("enabled"),
                rs.getBoolean("force_reject"),
                rs.getString("reject_reason_code"),
                rs.getString("reject_reason_message")
        );
    }

    private OperationsConnectorTestResponse buildResponse(
            String status,
            ConnectorConfigRow config,
            Boolean reachable,
            String responseCode,
            String responseMessage,
            long started
    ) {
        long responseTimeMs = System.currentTimeMillis() - started;

        return new OperationsConnectorTestResponse(
                status,
                LocalDateTime.now(),
                config.connectorName(),
                config.bankCode(),
                config.connectorType(),
                config.enabled(),
                config.forceReject(),
                reachable,
                responseCode,
                responseMessage,
                responseTimeMs
        );
    }

    private record ConnectorConfigRow(
            String connectorName,
            String bankCode,
            String connectorType,
            String endpointUrl,
            Integer timeoutMs,
            Boolean enabled,
            Boolean forceReject,
            String rejectReasonCode,
            String rejectReasonMessage
    ) {
    }
}