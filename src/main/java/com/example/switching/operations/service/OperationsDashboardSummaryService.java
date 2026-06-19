package com.example.switching.operations.service;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.example.switching.operations.dto.OperationsDashboardSummaryResponse;

@Service
public class OperationsDashboardSummaryService {

    private static final int DEFAULT_LATEST_LIMIT = 10;
    private static final int DEFAULT_ALERT_LIMIT = 10;
    private static final int STUCK_THRESHOLD_MINUTES = 5;

    private final JdbcTemplate jdbcTemplate;

    public OperationsDashboardSummaryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public OperationsDashboardSummaryResponse getDashboardSummary() {
        boolean databaseUp = isDatabaseUp();

        OperationsDashboardSummaryResponse.TransferSummary transfers = buildTransferSummary();
        OperationsDashboardSummaryResponse.OutboxSummary outbox = buildOutboxSummary();
        OperationsDashboardSummaryResponse.IsoMessageSummary isoMessages = buildIsoMessageSummary();
        OperationsDashboardSummaryResponse.BankSummary banks = buildBankSummary();
        OperationsDashboardSummaryResponse.ConnectorSummary connectors = buildConnectorSummary();
        OperationsDashboardSummaryResponse.RoutingSummary routingRules = buildRoutingSummary();

        List<OperationsDashboardSummaryResponse.LatestTransactionItem> latestTransactions =
                findLatestTransactions(DEFAULT_LATEST_LIMIT);

        List<OperationsDashboardSummaryResponse.OutboxAlertItem> failedOutboxEvents =
                findFailedOutboxEvents(DEFAULT_ALERT_LIMIT);

        List<OperationsDashboardSummaryResponse.OutboxAlertItem> stuckOutboxEvents =
                findStuckOutboxEvents(DEFAULT_ALERT_LIMIT, STUCK_THRESHOLD_MINUTES);

        String status = resolveStatus(
                databaseUp,
                transfers,
                outbox,
                banks,
                connectors,
                routingRules
        );

        String message = resolveMessage(status, databaseUp, outbox, banks, connectors, routingRules);

        return new OperationsDashboardSummaryResponse(
                status,
                LocalDateTime.now(),
                transfers,
                outbox,
                isoMessages,
                banks,
                connectors,
                routingRules,
                latestTransactions,
                failedOutboxEvents,
                stuckOutboxEvents,
                message
        );
    }

    private OperationsDashboardSummaryResponse.TransferSummary buildTransferSummary() {
        return new OperationsDashboardSummaryResponse.TransferSummary(
                count("SELECT COUNT(*) FROM transactions"),
                count("SELECT COUNT(*) FROM transactions WHERE status IN ('ACCEPTED', 'RECEIVED')"),
                count("SELECT COUNT(*) FROM transactions WHERE status IN ('SETTLED', 'SUCCESS')"),
                count("SELECT COUNT(*) FROM transactions WHERE status IN ('REJECTED', 'FAILED')")
        );
    }

    private OperationsDashboardSummaryResponse.OutboxSummary buildOutboxSummary() {
        return new OperationsDashboardSummaryResponse.OutboxSummary(
                count("SELECT COUNT(*) FROM outbox_messages"),
                count("SELECT COUNT(*) FROM outbox_messages WHERE status = 'PENDING'"),
                count("SELECT COUNT(*) FROM outbox_messages WHERE status = 'PROCESSING'"),
                count("SELECT COUNT(*) FROM outbox_messages WHERE status = 'SUCCESS'"),
                count("SELECT COUNT(*) FROM outbox_messages WHERE status = 'FAILED'"),
                count(
                        """
                        SELECT COUNT(*)
                        FROM outbox_messages
                        WHERE status = 'PROCESSING'
                          AND FLOOR(EXTRACT(EPOCH FROM (NOW() - COALESCE(updated_at, processed_at, created_at))) / 60) >= ?
                        """,
                        STUCK_THRESHOLD_MINUTES
                )
        );
    }

    private OperationsDashboardSummaryResponse.IsoMessageSummary buildIsoMessageSummary() {
        return new OperationsDashboardSummaryResponse.IsoMessageSummary(
                count("SELECT COUNT(*) FROM iso_messages"),
                count("SELECT COUNT(*) FROM iso_messages WHERE message_type = 'PACS_008' AND direction = 'OUTBOUND'"),
                count("SELECT COUNT(*) FROM iso_messages WHERE message_type = 'PACS_002' AND direction = 'INBOUND'"),
                count("SELECT COUNT(*) FROM iso_messages WHERE security_status = 'ENCRYPTED'"),
                count("SELECT COUNT(*) FROM iso_messages WHERE security_status = 'DECRYPTED'"),
                count("SELECT COUNT(*) FROM iso_messages WHERE validation_status = 'NOT_VALIDATED'"),
                count("SELECT COUNT(*) FROM iso_messages WHERE validation_status = 'VALID'"),
                count("SELECT COUNT(*) FROM iso_messages WHERE validation_status = 'INVALID'")
        );
    }

    private OperationsDashboardSummaryResponse.BankSummary buildBankSummary() {
        return new OperationsDashboardSummaryResponse.BankSummary(
                count("SELECT COUNT(*) FROM participants"),
                count("SELECT COUNT(*) FROM participants WHERE status = 'ACTIVE'"),
                count("SELECT COUNT(*) FROM participants WHERE status = 'INACTIVE'"),
                count("SELECT COUNT(*) FROM participants WHERE status = 'MAINTENANCE'")
        );
    }

    private OperationsDashboardSummaryResponse.ConnectorSummary buildConnectorSummary() {
        return new OperationsDashboardSummaryResponse.ConnectorSummary(
                count("SELECT COUNT(*) FROM connector_configs"),
                count("SELECT COUNT(*) FROM connector_configs WHERE enabled = true"),
                count("SELECT COUNT(*) FROM connector_configs WHERE enabled = false"),
                count("SELECT COUNT(*) FROM connector_configs WHERE force_reject = true")
        );
    }

    private OperationsDashboardSummaryResponse.RoutingSummary buildRoutingSummary() {
        return new OperationsDashboardSummaryResponse.RoutingSummary(
                count("SELECT COUNT(*) FROM routing_rules"),
                count("SELECT COUNT(*) FROM routing_rules WHERE enabled = true"),
                count("SELECT COUNT(*) FROM routing_rules WHERE enabled = false")
        );
    }

    private List<OperationsDashboardSummaryResponse.LatestTransactionItem> findLatestTransactions(int limit) {
        return jdbcTemplate.query(
                """
                SELECT
                    id,
                    transaction_ref,
                    inquiry_ref,
                    source_bank,
                    destination_bank,
                    amount,
                    currency,
                    status,
                    external_reference,
                    created_at
                FROM transactions
                ORDER BY created_at DESC, id DESC
                LIMIT ?
                """,
                (rs, rowNum) -> mapLatestTransaction(rs),
                limit
        );
    }

    private OperationsDashboardSummaryResponse.LatestTransactionItem mapLatestTransaction(ResultSet rs)
            throws java.sql.SQLException {
        String transactionRef = rs.getString("transaction_ref");

        return new OperationsDashboardSummaryResponse.LatestTransactionItem(
                rs.getLong("id"),
                transactionRef,
                rs.getString("inquiry_ref"),
                rs.getString("source_bank"),
                rs.getString("destination_bank"),
                rs.getBigDecimal("amount"),
                rs.getString("currency"),
                rs.getString("status"),
                rs.getString("external_reference"),
                toLocalDateTime(rs.getTimestamp("created_at")),
                "/api/transfers/" + transactionRef + "/trace"
        );
    }

    private List<OperationsDashboardSummaryResponse.OutboxAlertItem> findFailedOutboxEvents(int limit) {
        return jdbcTemplate.query(
                """
                SELECT
                    id,
                    transaction_ref,
                    message_type,
                    status,
                    retry_count,
                    last_error,
                    created_at,
                    updated_at,
                    NULL AS stuck_minutes
                FROM outbox_messages
                WHERE status = 'FAILED'
                ORDER BY updated_at DESC, created_at DESC, id DESC
                LIMIT ?
                """,
                (rs, rowNum) -> mapOutboxAlert(rs, true),
                limit
        );
    }

    private List<OperationsDashboardSummaryResponse.OutboxAlertItem> findStuckOutboxEvents(
            int limit,
            int thresholdMinutes
    ) {
        return jdbcTemplate.query(
                """
                SELECT
                    id,
                    transaction_ref,
                    message_type,
                    status,
                    retry_count,
                    last_error,
                    created_at,
                    updated_at,
                    FLOOR(EXTRACT(EPOCH FROM (NOW() - COALESCE(updated_at, processed_at, created_at))) / 60) AS stuck_minutes
                FROM outbox_messages
                WHERE status = 'PROCESSING'
                  AND FLOOR(EXTRACT(EPOCH FROM (NOW() - COALESCE(updated_at, processed_at, created_at))) / 60) >= ?
                ORDER BY COALESCE(updated_at, processed_at, created_at) ASC, id ASC
                LIMIT ?
                """,
                (rs, rowNum) -> mapOutboxAlert(rs, false),
                thresholdMinutes,
                limit
        );
    }

    private OperationsDashboardSummaryResponse.OutboxAlertItem mapOutboxAlert(ResultSet rs, boolean failed)
            throws java.sql.SQLException {
        Long id = rs.getLong("id");

        return new OperationsDashboardSummaryResponse.OutboxAlertItem(
                id,
                rs.getString("transaction_ref"),
                rs.getString("message_type"),
                rs.getString("status"),
                rs.getInt("retry_count"),
                rs.getString("last_error"),
                toLocalDateTime(rs.getTimestamp("created_at")),
                toLocalDateTime(rs.getTimestamp("updated_at")),
                getNullableLong(rs, "stuck_minutes"),
                failed
                        ? "/api/outbox-events/" + id + "/retry"
                        : "/api/operations/outbox-stuck/recover-all"
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

    private String resolveStatus(
            boolean databaseUp,
            OperationsDashboardSummaryResponse.TransferSummary transfers,
            OperationsDashboardSummaryResponse.OutboxSummary outbox,
            OperationsDashboardSummaryResponse.BankSummary banks,
            OperationsDashboardSummaryResponse.ConnectorSummary connectors,
            OperationsDashboardSummaryResponse.RoutingSummary routingRules
    ) {
        if (!databaseUp) {
            return "DOWN";
        }

        if (hasUnknown(transfers.total(), transfers.received(), transfers.success(), transfers.failed())
                || hasUnknown(outbox.total(), outbox.pending(), outbox.processing(), outbox.success(), outbox.failed(), outbox.stuckProcessing())
                || hasUnknown(banks.total(), banks.active(), banks.inactive(), banks.maintenance())
                || hasUnknown(connectors.total(), connectors.enabled(), connectors.disabled(), connectors.forceReject())
                || hasUnknown(routingRules.total(), routingRules.enabled(), routingRules.disabled())) {
            return "DEGRADED";
        }

        if (outbox.failed() > 0 || outbox.stuckProcessing() > 0) {
            return "DEGRADED";
        }

        if (banks.active() <= 0 || connectors.enabled() <= 0 || routingRules.enabled() <= 0) {
            return "DEGRADED";
        }

        return "HEALTHY";
    }

    private String resolveMessage(
            String status,
            boolean databaseUp,
            OperationsDashboardSummaryResponse.OutboxSummary outbox,
            OperationsDashboardSummaryResponse.BankSummary banks,
            OperationsDashboardSummaryResponse.ConnectorSummary connectors,
            OperationsDashboardSummaryResponse.RoutingSummary routingRules
    ) {
        if (!databaseUp) {
            return "Database is not reachable";
        }

        if ("HEALTHY".equals(status)) {
            return "Switching operations dashboard is healthy";
        }

        if (outbox.failed() != null && outbox.failed() > 0) {
            return "There are failed outbox events that require retry or review";
        }

        if (outbox.stuckProcessing() != null && outbox.stuckProcessing() > 0) {
            return "There are stuck PROCESSING outbox events that require recovery";
        }

        if (banks.active() != null && banks.active() <= 0) {
            return "No active participants found";
        }

        if (connectors.enabled() != null && connectors.enabled() <= 0) {
            return "No enabled connector configs found";
        }

        if (routingRules.enabled() != null && routingRules.enabled() <= 0) {
            return "No enabled routing rules found";
        }

        return "Switching is running but some checks are degraded";
    }

    private Long count(String sql, Object... args) {
        try {
            Long result = jdbcTemplate.queryForObject(sql, Long.class, args);
            return result == null ? 0L : result;
        } catch (Exception ex) {
            return -1L;
        }
    }

    private boolean hasUnknown(Long... values) {
        if (values == null) {
            return false;
        }

        for (Long value : values) {
            if (value != null && value < 0) {
                return true;
            }
        }

        return false;
    }

    private Long getNullableLong(ResultSet rs, String columnName) throws java.sql.SQLException {
        long value = rs.getLong(columnName);

        if (rs.wasNull()) {
            return null;
        }

        return value;
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        if (timestamp == null) {
            return null;
        }

        return timestamp.toLocalDateTime();
    }
}
