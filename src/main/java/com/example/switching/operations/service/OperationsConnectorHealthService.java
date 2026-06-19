package com.example.switching.operations.service;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.example.switching.operations.dto.OperationsConnectorHealthItemResponse;
import com.example.switching.operations.dto.OperationsConnectorHealthListResponse;

@Service
public class OperationsConnectorHealthService {

    private final JdbcTemplate jdbcTemplate;

    public OperationsConnectorHealthService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public OperationsConnectorHealthListResponse getConnectorHealth(String connectorName) {
        List<ConnectorConfigRow> configs = findConnectorConfigs(connectorName);

        List<OperationsConnectorHealthItemResponse> items = configs.stream()
                .map(this::buildHealthItem)
                .toList();

        String status = resolveOverallStatus(items);

        return new OperationsConnectorHealthListResponse(
                status,
                LocalDateTime.now(),
                items.size(),
                items
        );
    }

    private List<ConnectorConfigRow> findConnectorConfigs(String connectorName) {
        if (StringUtils.hasText(connectorName)) {
            return jdbcTemplate.query(
                    """
                    SELECT
                        c.id,
                        c.connector_name,
                        c.bank_code,
                        p.bank_name,
                        p.status AS participant_status,
                        c.connector_type,
                        c.endpoint_url,
                        c.timeout_ms,
                        c.enabled,
                        c.force_reject,
                        c.reject_reason_code,
                        c.reject_reason_message,
                        c.created_at,
                        c.updated_at
                    FROM connector_configs c
                    LEFT JOIN participants p ON p.bank_code = c.bank_code
                    WHERE c.connector_name = ?
                    ORDER BY c.connector_name ASC
                    """,
                    (rs, rowNum) -> mapConnectorConfig(rs),
                    connectorName.trim().toUpperCase()
            );
        }

        return jdbcTemplate.query(
                """
                SELECT
                    c.id,
                    c.connector_name,
                    c.bank_code,
                    p.bank_name,
                    p.status AS participant_status,
                    c.connector_type,
                    c.endpoint_url,
                    c.timeout_ms,
                    c.enabled,
                    c.force_reject,
                    c.reject_reason_code,
                    c.reject_reason_message,
                    c.created_at,
                    c.updated_at
                FROM connector_configs c
                LEFT JOIN participants p ON p.bank_code = c.bank_code
                ORDER BY c.connector_name ASC
                """,
                (rs, rowNum) -> mapConnectorConfig(rs)
        );
    }

    private ConnectorConfigRow mapConnectorConfig(ResultSet rs) throws java.sql.SQLException {
        return new ConnectorConfigRow(
                rs.getLong("id"),
                rs.getString("connector_name"),
                rs.getString("bank_code"),
                rs.getString("bank_name"),
                rs.getString("participant_status"),
                rs.getString("connector_type"),
                rs.getString("endpoint_url"),
                rs.getInt("timeout_ms"),
                rs.getBoolean("enabled"),
                rs.getBoolean("force_reject"),
                rs.getString("reject_reason_code"),
                rs.getString("reject_reason_message"),
                toLocalDateTime(rs.getTimestamp("created_at")),
                toLocalDateTime(rs.getTimestamp("updated_at"))
        );
    }

    private OperationsConnectorHealthItemResponse buildHealthItem(ConnectorConfigRow config) {
        Long inboundRouteTotal = count(
                "SELECT COUNT(*) FROM routing_rules WHERE connector_name = ?",
                config.connectorName()
        );

        Long inboundRouteEnabled = count(
                "SELECT COUNT(*) FROM routing_rules WHERE connector_name = ? AND enabled = true",
                config.connectorName()
        );

        Long outboundRouteTotal = count(
                "SELECT COUNT(*) FROM routing_rules WHERE source_bank = ?",
                config.bankCode()
        );

        Long outboundRouteEnabled = count(
                "SELECT COUNT(*) FROM routing_rules WHERE source_bank = ? AND enabled = true",
                config.bankCode()
        );

        Long relatedTransferTotal = count(
                """
                SELECT COUNT(*)
                FROM transactions
                WHERE source_bank = ?
                   OR destination_bank = ?
                """,
                config.bankCode(),
                config.bankCode()
        );

        Long relatedTransferSuccess = count(
                """
                SELECT COUNT(*)
                FROM transactions
                WHERE status IN ('SETTLED', 'SUCCESS')
                  AND (source_bank = ? OR destination_bank = ?)
                """,
                config.bankCode(),
                config.bankCode()
        );

        Long relatedTransferFailed = count(
                """
                SELECT COUNT(*)
                FROM transactions
                WHERE status IN ('REJECTED', 'FAILED')
                  AND (source_bank = ? OR destination_bank = ?)
                """,
                config.bankCode(),
                config.bankCode()
        );

        Long relatedOutboxFailed = count(
                """
                SELECT COUNT(*)
                FROM outbox_messages o
                JOIN transactions t ON t.transaction_ref = o.transaction_ref
                WHERE o.status = 'FAILED'
                  AND (t.source_bank = ? OR t.destination_bank = ?)
                """,
                config.bankCode(),
                config.bankCode()
        );

        Long relatedOutboxProcessing = count(
                """
                SELECT COUNT(*)
                FROM outbox_messages o
                JOIN transactions t ON t.transaction_ref = o.transaction_ref
                WHERE o.status = 'PROCESSING'
                  AND (t.source_bank = ? OR t.destination_bank = ?)
                """,
                config.bankCode(),
                config.bankCode()
        );

        Long relatedOutboxPending = count(
                """
                SELECT COUNT(*)
                FROM outbox_messages o
                JOIN transactions t ON t.transaction_ref = o.transaction_ref
                WHERE o.status = 'PENDING'
                  AND (t.source_bank = ? OR t.destination_bank = ?)
                """,
                config.bankCode(),
                config.bankCode()
        );

        String healthStatus = resolveHealthStatus(
                config,
                inboundRouteEnabled,
                relatedOutboxFailed,
                relatedOutboxProcessing
        );

        String healthMessage = resolveHealthMessage(
                config,
                inboundRouteEnabled,
                relatedOutboxFailed,
                relatedOutboxProcessing
        );

        return new OperationsConnectorHealthItemResponse(
                config.id(),
                config.connectorName(),
                config.bankCode(),
                config.bankName(),
                config.participantStatus(),
                config.connectorType(),
                config.endpointUrl(),
                config.timeoutMs(),
                config.enabled(),
                config.forceReject(),
                config.rejectReasonCode(),
                config.rejectReasonMessage(),

                healthStatus,
                healthMessage,

                inboundRouteTotal,
                inboundRouteEnabled,
                outboundRouteTotal,
                outboundRouteEnabled,

                relatedTransferTotal,
                relatedTransferSuccess,
                relatedTransferFailed,

                relatedOutboxFailed,
                relatedOutboxProcessing,
                relatedOutboxPending,

                config.createdAt(),
                config.updatedAt(),
                "/api/operations/connectors/" + config.connectorName() + "/test"
        );
    }

    private String resolveHealthStatus(
            ConnectorConfigRow config,
            Long inboundRouteEnabled,
            Long relatedOutboxFailed,
            Long relatedOutboxProcessing
    ) {
        if (!"ACTIVE".equalsIgnoreCase(config.participantStatus())) {
            return "DOWN";
        }

        if (!config.enabled()) {
            return "DOWN";
        }

        if (config.forceReject()) {
            return "DEGRADED";
        }

        if (inboundRouteEnabled == null || inboundRouteEnabled <= 0) {
            return "DEGRADED";
        }

        if (relatedOutboxFailed != null && relatedOutboxFailed > 0) {
            return "DEGRADED";
        }

        if (relatedOutboxProcessing != null && relatedOutboxProcessing > 0) {
            return "DEGRADED";
        }

        return "HEALTHY";
    }

    private String resolveHealthMessage(
            ConnectorConfigRow config,
            Long inboundRouteEnabled,
            Long relatedOutboxFailed,
            Long relatedOutboxProcessing
    ) {
        if (!"ACTIVE".equalsIgnoreCase(config.participantStatus())) {
            return "Participant is not ACTIVE";
        }

        if (!config.enabled()) {
            return "Connector is disabled";
        }

        if (config.forceReject()) {
            return "Connector is configured to force reject";
        }

        if (inboundRouteEnabled == null || inboundRouteEnabled <= 0) {
            return "No enabled routing rule is using this connector";
        }

        if (relatedOutboxFailed != null && relatedOutboxFailed > 0) {
            return "There are failed outbox events related to this connector bank";
        }

        if (relatedOutboxProcessing != null && relatedOutboxProcessing > 0) {
            return "There are processing outbox events related to this connector bank";
        }

        return "Connector is operational";
    }

    private String resolveOverallStatus(List<OperationsConnectorHealthItemResponse> items) {
        if (items.isEmpty()) {
            return "EMPTY";
        }

        boolean hasDown = items.stream().anyMatch(item -> "DOWN".equals(item.healthStatus()));
        boolean hasDegraded = items.stream().anyMatch(item -> "DEGRADED".equals(item.healthStatus()));

        if (hasDown) {
            return "HAS_DOWN_CONNECTORS";
        }

        if (hasDegraded) {
            return "HAS_DEGRADED_CONNECTORS";
        }

        return "HEALTHY";
    }

    private Long count(String sql, Object... args) {
        try {
            Long result = jdbcTemplate.queryForObject(sql, Long.class, args);
            return result == null ? 0L : result;
        } catch (Exception ex) {
            return -1L;
        }
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        if (timestamp == null) {
            return null;
        }

        return timestamp.toLocalDateTime();
    }

    private record ConnectorConfigRow(
            Long id,
            String connectorName,
            String bankCode,
            String bankName,
            String participantStatus,
            String connectorType,
            String endpointUrl,
            Integer timeoutMs,
            Boolean enabled,
            Boolean forceReject,
            String rejectReasonCode,
            String rejectReasonMessage,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }
}
