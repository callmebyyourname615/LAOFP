package com.example.switching.operations.service;

import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.example.switching.audit.service.AuditLogService;
import com.example.switching.common.util.AuditActorUtil;
import com.example.switching.operations.dto.OperationsGenerateRouteItemResponse;
import com.example.switching.operations.dto.OperationsGenerateRoutesForBankRequest;
import com.example.switching.operations.dto.OperationsGenerateRoutesForBankResponse;

@Service
public class OperationsGenerateRoutesForBankService {

    private static final String DEFAULT_MESSAGE_TYPE = "PACS_008";
    private static final String DEFAULT_MODE = "BOTH";
    private static final String ENTITY_TYPE = "ROUTING_RULE";

    private final JdbcTemplate jdbcTemplate;
    private final AuditLogService auditLogService;

    public OperationsGenerateRoutesForBankService(
            JdbcTemplate jdbcTemplate,
            AuditLogService auditLogService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public OperationsGenerateRoutesForBankResponse generateForBank(
            OperationsGenerateRoutesForBankRequest request
    ) {
        String bankCode = requiredUpper(request.bankCode(), "bankCode");
        String messageType = defaultUpper(request.messageType(), DEFAULT_MESSAGE_TYPE);
        String mode = defaultUpper(request.mode(), DEFAULT_MODE);
        int priority = normalizePositive(request.priority(), 1, "priority");
        boolean enabled = request.enabled() == null || request.enabled();

        validateMode(mode);
        validateParticipantActive(bankCode);

        List<ParticipantRow> activeParticipants = findActiveParticipants();
        List<OperationsGenerateRouteItemResponse> items = new ArrayList<>();

        if ("INBOUND_ONLY".equals(mode) || "BOTH".equals(mode)) {
            for (ParticipantRow source : activeParticipants) {
                if (!source.bankCode().equals(bankCode)) {
                    items.add(generateRouteIfNeeded(
                            source.bankCode(),
                            bankCode,
                            messageType,
                            priority,
                            enabled
                    ));
                }
            }
        }

        if ("OUTBOUND_ONLY".equals(mode) || "BOTH".equals(mode)) {
            for (ParticipantRow destination : activeParticipants) {
                if (!destination.bankCode().equals(bankCode)) {
                    items.add(generateRouteIfNeeded(
                            bankCode,
                            destination.bankCode(),
                            messageType,
                            priority,
                            enabled
                    ));
                }
            }
        }

        int createdCount = (int) items.stream()
                .filter(item -> "CREATED".equals(item.action()))
                .count();

        int skippedCount = items.size() - createdCount;

        String status;
        if (items.isEmpty()) {
            status = "NO_CANDIDATES";
        } else if (createdCount == items.size()) {
            status = "CREATED";
        } else if (createdCount > 0) {
            status = "PARTIALLY_CREATED";
        } else {
            status = "SKIPPED";
        }

        auditGenerateRoutes(
                bankCode,
                messageType,
                mode,
                items.size(),
                createdCount,
                skippedCount,
                status
        );

        return new OperationsGenerateRoutesForBankResponse(
                status,
                LocalDateTime.now(),
                bankCode,
                messageType,
                mode,
                items.size(),
                createdCount,
                skippedCount,
                items
        );
    }

    private OperationsGenerateRouteItemResponse generateRouteIfNeeded(
            String sourceBank,
            String destinationBank,
            String messageType,
            int priority,
            boolean enabled
    ) {
        if (routeExists(sourceBank, destinationBank, messageType)) {
            return new OperationsGenerateRouteItemResponse(
                    sourceBank,
                    destinationBank,
                    messageType,
                    buildRouteCode(sourceBank, destinationBank, messageType),
                    null,
                    "SKIPPED",
                    "Routing rule already exists for source/destination/messageType"
            );
        }

        String connectorName = findEnabledConnectorName(destinationBank);

        if (!StringUtils.hasText(connectorName)) {
            return new OperationsGenerateRouteItemResponse(
                    sourceBank,
                    destinationBank,
                    messageType,
                    buildRouteCode(sourceBank, destinationBank, messageType),
                    null,
                    "SKIPPED",
                    "No enabled connector found for destination bank"
            );
        }

        String routeCode = buildRouteCode(sourceBank, destinationBank, messageType);

        jdbcTemplate.update(
                """
                INSERT INTO routing_rules (
                    route_code,
                    source_bank,
                    destination_bank,
                    message_type,
                    connector_name,
                    priority,
                    enabled,
                    created_at,
                    updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
                """,
                routeCode,
                sourceBank,
                destinationBank,
                messageType,
                connectorName,
                priority,
                enabled
        );

        return new OperationsGenerateRouteItemResponse(
                sourceBank,
                destinationBank,
                messageType,
                routeCode,
                connectorName,
                "CREATED",
                "Routing rule generated successfully"
        );
    }

    private List<ParticipantRow> findActiveParticipants() {
        return jdbcTemplate.query(
                """
                SELECT bank_code
                FROM participants
                WHERE status = 'ACTIVE'
                ORDER BY bank_code ASC
                """,
                (rs, rowNum) -> mapParticipant(rs)
        );
    }

    private ParticipantRow mapParticipant(ResultSet rs) throws java.sql.SQLException {
        return new ParticipantRow(rs.getString("bank_code"));
    }

    private void validateParticipantActive(String bankCode) {
        Long count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM participants
                WHERE bank_code = ?
                  AND status = 'ACTIVE'
                """,
                Long.class,
                bankCode
        );

        if (count == null || count == 0) {
            throw new IllegalArgumentException("bankCode does not exist or is not ACTIVE: " + bankCode);
        }
    }

    private boolean routeExists(String sourceBank, String destinationBank, String messageType) {
        Long count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM routing_rules
                WHERE source_bank = ?
                  AND destination_bank = ?
                  AND message_type = ?
                """,
                Long.class,
                sourceBank,
                destinationBank,
                messageType
        );

        return count != null && count > 0;
    }

    private String findEnabledConnectorName(String bankCode) {
        List<String> connectors = jdbcTemplate.query(
                """
                SELECT connector_name
                FROM connector_configs
                WHERE bank_code = ?
                  AND enabled = true
                ORDER BY id ASC
                LIMIT 1
                """,
                (rs, rowNum) -> rs.getString("connector_name"),
                bankCode
        );

        if (connectors.isEmpty()) {
            return null;
        }

        return connectors.get(0);
    }

    private String buildRouteCode(String sourceBank, String destinationBank, String messageType) {
        return "ROUTE_"
                + sourceBank
                + "_TO_"
                + destinationBank
                + "_"
                + messageType
                + "_AUTO";
    }

    private String requiredUpper(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(fieldName + " is required");
        }

        return value.trim().toUpperCase();
    }

    private String defaultUpper(String value, String defaultValue) {
        if (!StringUtils.hasText(value)) {
            return defaultValue;
        }

        return value.trim().toUpperCase();
    }

    private int normalizePositive(Integer value, int defaultValue, String fieldName) {
        if (value == null) {
            return defaultValue;
        }

        if (value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be greater than 0");
        }

        return value;
    }

    private void validateMode(String mode) {
        if (!"INBOUND_ONLY".equals(mode)
                && !"OUTBOUND_ONLY".equals(mode)
                && !"BOTH".equals(mode)) {
            throw new IllegalArgumentException(
                    "mode must be one of INBOUND_ONLY, OUTBOUND_ONLY, BOTH"
            );
        }
    }

    private void auditGenerateRoutes(
            String bankCode,
            String messageType,
            String mode,
            int totalCandidates,
            int createdCount,
            int skippedCount,
            String status
    ) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("bankCode", bankCode);
            payload.put("messageType", messageType);
            payload.put("mode", mode);
            payload.put("totalCandidates", totalCandidates);
            payload.put("createdCount", createdCount);
            payload.put("skippedCount", skippedCount);
            payload.put("status", status);
            payload.put("processedAt", LocalDateTime.now().toString());

            auditLogService.log(
                    "ROUTING_RULES_GENERATED_FOR_BANK",
                    ENTITY_TYPE,
                    bankCode,
                    AuditActorUtil.currentActor(),
                    payload
            );
        } catch (Exception ignored) {
            /*
             * Do not rollback route generation because audit logging failed.
             */
        }
    }

    private record ParticipantRow(String bankCode) {
    }
}
