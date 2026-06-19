package com.example.switching.operations.service;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.example.switching.operations.dto.OperationsBankStatusItemResponse;
import com.example.switching.operations.dto.OperationsBankStatusListResponse;

@Service
public class OperationsBankStatusService {

    private final JdbcTemplate jdbcTemplate;

    public OperationsBankStatusService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public OperationsBankStatusListResponse getBankStatus(String bankCode) {
        List<ParticipantRow> participants = findParticipants(bankCode);

        List<OperationsBankStatusItemResponse> items = participants.stream()
                .map(this::buildBankStatus)
                .toList();

        String status = resolveOverallStatus(items);

        return new OperationsBankStatusListResponse(
                status,
                LocalDateTime.now(),
                items.size(),
                items
        );
    }

    private List<ParticipantRow> findParticipants(String bankCode) {
        if (StringUtils.hasText(bankCode)) {
            return jdbcTemplate.query(
                    """
                    SELECT
                        bank_code,
                        bank_name,
                        participant_type,
                        status,
                        country,
                        currency
                    FROM participants
                    WHERE bank_code = ?
                    ORDER BY bank_code ASC
                    """,
                    (rs, rowNum) -> mapParticipant(rs),
                    bankCode.trim()
            );
        }

        return jdbcTemplate.query(
                """
                SELECT
                    bank_code,
                    bank_name,
                    participant_type,
                    status,
                    country,
                    currency
                FROM participants
                ORDER BY bank_code ASC
                """,
                (rs, rowNum) -> mapParticipant(rs)
        );
    }

    private ParticipantRow mapParticipant(ResultSet rs) throws java.sql.SQLException {
        return new ParticipantRow(
                rs.getString("bank_code"),
                rs.getString("bank_name"),
                rs.getString("participant_type"),
                rs.getString("status"),
                rs.getString("country"),
                rs.getString("currency")
        );
    }

    private OperationsBankStatusItemResponse buildBankStatus(ParticipantRow participant) {
        String bankCode = participant.bankCode();

        Long connectorTotal = count(
                "SELECT COUNT(*) FROM connector_configs WHERE bank_code = ?",
                bankCode
        );

        Long connectorEnabled = count(
                "SELECT COUNT(*) FROM connector_configs WHERE bank_code = ? AND enabled = true",
                bankCode
        );

        Long connectorDisabled = count(
                "SELECT COUNT(*) FROM connector_configs WHERE bank_code = ? AND enabled = false",
                bankCode
        );

        Long connectorForceReject = count(
                "SELECT COUNT(*) FROM connector_configs WHERE bank_code = ? AND force_reject = true",
                bankCode
        );

        Long outboundRouteTotal = count(
                "SELECT COUNT(*) FROM routing_rules WHERE source_bank = ?",
                bankCode
        );

        Long outboundRouteEnabled = count(
                "SELECT COUNT(*) FROM routing_rules WHERE source_bank = ? AND enabled = true",
                bankCode
        );

        Long inboundRouteTotal = count(
                "SELECT COUNT(*) FROM routing_rules WHERE destination_bank = ?",
                bankCode
        );

        Long inboundRouteEnabled = count(
                "SELECT COUNT(*) FROM routing_rules WHERE destination_bank = ? AND enabled = true",
                bankCode
        );

               Long outboundTransferTotal = count(
                "SELECT COUNT(*) FROM transactions WHERE source_bank = ?",
                bankCode
        );

        Long outboundTransferSuccess = count(
                "SELECT COUNT(*) FROM transactions WHERE source_bank = ? AND status IN ('SETTLED', 'SUCCESS')",
                bankCode
        );

        Long outboundTransferFailed = count(
                "SELECT COUNT(*) FROM transactions WHERE source_bank = ? AND status IN ('REJECTED', 'FAILED')",
                bankCode
        );

        Long inboundTransferTotal = count(
                "SELECT COUNT(*) FROM transactions WHERE destination_bank = ?",
                bankCode
        );

        Long inboundTransferSuccess = count(
                "SELECT COUNT(*) FROM transactions WHERE destination_bank = ? AND status IN ('SETTLED', 'SUCCESS')",
                bankCode
        );

        Long inboundTransferFailed = count(
                "SELECT COUNT(*) FROM transactions WHERE destination_bank = ? AND status IN ('REJECTED', 'FAILED')",
                bankCode
        );

        Long relatedOutboxFailed = count(
                """
                SELECT COUNT(*)
                FROM outbox_messages o
                JOIN transactions t ON t.transaction_ref = o.transaction_ref
                WHERE o.status = 'FAILED'
                  AND (t.source_bank = ? OR t.destination_bank = ?)
                """,
                bankCode,
                bankCode
        );

        Long relatedOutboxPending = count(
                """
                SELECT COUNT(*)
                FROM outbox_messages o
                JOIN transactions t ON t.transaction_ref = o.transaction_ref
                WHERE o.status = 'PENDING'
                  AND (t.source_bank = ? OR t.destination_bank = ?)
                """,
                bankCode,
                bankCode
        );

        Long relatedOutboxProcessing = count(
                """
                SELECT COUNT(*)
                FROM outbox_messages o
                JOIN transactions t ON t.transaction_ref = o.transaction_ref
                WHERE o.status = 'PROCESSING'
                  AND (t.source_bank = ? OR t.destination_bank = ?)
                """,
                bankCode,
                bankCode
        );

        LocalDateTime lastOutboundTransferAt = dateTime(
                "SELECT MAX(created_at) FROM transactions WHERE source_bank = ?",
                bankCode
        );

        LocalDateTime lastInboundTransferAt = dateTime(
                "SELECT MAX(created_at) FROM transactions WHERE destination_bank = ?",
                bankCode
        );

        String healthStatus = resolveBankHealthStatus(
                participant,
                connectorEnabled,
                outboundRouteEnabled,
                inboundRouteEnabled,
                outboundTransferFailed,
                inboundTransferFailed,
                relatedOutboxFailed,
                relatedOutboxProcessing
        );

        String healthMessage = resolveBankHealthMessage(
                participant,
                connectorEnabled,
                outboundRouteEnabled,
                inboundRouteEnabled,
                relatedOutboxFailed,
                relatedOutboxProcessing
        );

        return new OperationsBankStatusItemResponse(
                participant.bankCode(),
                participant.bankName(),
                participant.participantType(),
                participant.status(),
                participant.country(),
                participant.currency(),

                healthStatus,
                healthMessage,

                connectorTotal,
                connectorEnabled,
                connectorDisabled,
                connectorForceReject,

                outboundRouteTotal,
                outboundRouteEnabled,
                inboundRouteTotal,
                inboundRouteEnabled,

                outboundTransferTotal,
                outboundTransferSuccess,
                outboundTransferFailed,
                inboundTransferTotal,
                inboundTransferSuccess,
                inboundTransferFailed,

                relatedOutboxFailed,
                relatedOutboxPending,
                relatedOutboxProcessing,

                lastOutboundTransferAt,
                lastInboundTransferAt
        );
    }

    private String resolveOverallStatus(List<OperationsBankStatusItemResponse> items) {
        if (items.isEmpty()) {
            return "EMPTY";
        }

        boolean hasDegraded = items.stream()
                .anyMatch(item -> "DEGRADED".equals(item.healthStatus()));

        boolean hasDown = items.stream()
                .anyMatch(item -> "DOWN".equals(item.healthStatus()));

        if (hasDown) {
            return "HAS_DOWN_BANKS";
        }

        if (hasDegraded) {
            return "HAS_DEGRADED_BANKS";
        }

        return "HEALTHY";
    }

    private String resolveBankHealthStatus(
            ParticipantRow participant,
            Long connectorEnabled,
            Long outboundRouteEnabled,
            Long inboundRouteEnabled,
            Long outboundTransferFailed,
            Long inboundTransferFailed,
            Long relatedOutboxFailed,
            Long relatedOutboxProcessing
    ) {
        if (!"ACTIVE".equalsIgnoreCase(participant.status())) {
            return "DOWN";
        }

        if (connectorEnabled == null || connectorEnabled == 0) {
            return "DEGRADED";
        }

        if ((outboundRouteEnabled == null || outboundRouteEnabled == 0)
                && (inboundRouteEnabled == null || inboundRouteEnabled == 0)) {
            return "DEGRADED";
        }

        if (relatedOutboxFailed != null && relatedOutboxFailed > 0) {
            return "DEGRADED";
        }

        if (relatedOutboxProcessing != null && relatedOutboxProcessing > 0) {
            return "DEGRADED";
        }

        if ((outboundTransferFailed != null && outboundTransferFailed > 0)
                || (inboundTransferFailed != null && inboundTransferFailed > 0)) {
            return "DEGRADED";
        }

        return "HEALTHY";
    }

    private String resolveBankHealthMessage(
            ParticipantRow participant,
            Long connectorEnabled,
            Long outboundRouteEnabled,
            Long inboundRouteEnabled,
            Long relatedOutboxFailed,
            Long relatedOutboxProcessing
    ) {
        if (!"ACTIVE".equalsIgnoreCase(participant.status())) {
            return "Participant is not ACTIVE";
        }

        if (connectorEnabled == null || connectorEnabled == 0) {
            return "No enabled connector config for this bank";
        }

        if ((outboundRouteEnabled == null || outboundRouteEnabled == 0)
                && (inboundRouteEnabled == null || inboundRouteEnabled == 0)) {
            return "No enabled routing rule for this bank";
        }

        if (relatedOutboxFailed != null && relatedOutboxFailed > 0) {
            return "There are failed outbox events related to this bank";
        }

        if (relatedOutboxProcessing != null && relatedOutboxProcessing > 0) {
            return "There are processing outbox events related to this bank";
        }

        return "Bank is operational";
    }

    private Long count(String sql, Object... args) {
        try {
            Long result = jdbcTemplate.queryForObject(sql, Long.class, args);
            return result == null ? 0L : result;
        } catch (Exception ex) {
            return -1L;
        }
    }

    private LocalDateTime dateTime(String sql, Object... args) {
        try {
            Timestamp timestamp = jdbcTemplate.queryForObject(sql, Timestamp.class, args);

            if (timestamp == null) {
                return null;
            }

            return timestamp.toLocalDateTime();
        } catch (Exception ex) {
            return null;
        }
    }

    private record ParticipantRow(
            String bankCode,
            String bankName,
            String participantType,
            String status,
            String country,
            String currency
    ) {
    }
}
