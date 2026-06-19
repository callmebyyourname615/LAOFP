package com.example.switching.operations.service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.example.switching.audit.service.AuditLogService;
import com.example.switching.operations.dto.OperationsBankOnboardingRequest;
import com.example.switching.operations.dto.OperationsBankOnboardingResponse;

@Service
public class OperationsBankOnboardingService {

    private static final String ACTOR = "API";
    private static final String ENTITY_TYPE = "PARTICIPANT";

    private static final Set<String> ALLOWED_CONNECTOR_TYPES = Set.of("MOCK", "HTTP", "MQ");
    private static final Set<String> ALLOWED_MESSAGE_TYPES = Set.of("PACS_008", "PACS_002", "PACS_028", "PACS_004");

    private final JdbcTemplate jdbcTemplate;
    private final AuditLogService auditLogService;

    public OperationsBankOnboardingService(
            JdbcTemplate jdbcTemplate,
            AuditLogService auditLogService) {
        this.jdbcTemplate = jdbcTemplate;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public OperationsBankOnboardingResponse onboardBank(OperationsBankOnboardingRequest request) {
        String bankCode = requiredUpper(request.bankCode(), "bankCode");
        String bankName = requiredText(request.bankName(), "bankName");
        String participantType = defaultUpper(request.participantType(), "BANK");
        String participantStatus = defaultUpper(request.participantStatus(), "ACTIVE");
        String country = defaultUpper(request.country(), "TH");
        String currency = defaultUpper(request.currency(), "THB");

        String connectorType = defaultUpper(request.connectorType(), "MOCK");
        String connectorName = defaultUpper(
                request.connectorName(),
                connectorType + "_" + bankCode + "_CONNECTOR");

        String sourceBank = requiredUpper(request.sourceBank(), "sourceBank");
        String messageType = defaultUpper(request.messageType(), "PACS_008");
        String routeCode = defaultUpper(
                request.routeCode(),
                "ROUTE_" + sourceBank + "_TO_" + bankCode + "_" + messageType + "_PRIMARY");

        int timeoutMs = normalizePositive(request.timeoutMs(), 5000, "timeoutMs");
        int priority = normalizePositive(request.priority(), 1, "priority");
        boolean connectorEnabled = request.connectorEnabled() == null || request.connectorEnabled();
        boolean forceReject = request.forceReject() != null && request.forceReject();
        boolean routeEnabled = request.routeEnabled() == null || request.routeEnabled();

        validateConnectorType(connectorType);
        validateMessageType(messageType);

        if (!participantExists(sourceBank)) {
            throw new IllegalArgumentException("sourceBank does not exist in participants: " + sourceBank);
        }

        boolean participantAlreadyExisted = participantExists(bankCode);
        boolean participantCreated = false;

        if (!participantAlreadyExisted) {
            jdbcTemplate.update(
                    """
                            INSERT INTO participants (
                                bank_code,
                                bank_name,
                                participant_type,
                                status,
                                country,
                                currency,
                                created_at,
                                updated_at
                            )
                            VALUES (?, ?, ?, ?, ?, ?, NOW(), NOW())
                            """,
                    bankCode,
                    bankName,
                    participantType,
                    participantStatus,
                    country,
                    currency);

            participantCreated = true;
        }

        boolean connectorAlreadyExisted = connectorExists(connectorName);
        boolean connectorCreated = false;

        if (!connectorAlreadyExisted) {
            jdbcTemplate.update(
                    """
                            INSERT INTO connector_configs (
                                connector_name,
                                bank_code,
                                connector_type,
                                endpoint_url,
                                timeout_ms,
                                enabled,
                                force_reject,
                                reject_reason_code,
                                reject_reason_message,
                                created_at,
                                updated_at
                            )
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
                            """,
                    connectorName,
                    bankCode,
                    connectorType,
                    request.endpointUrl(),
                    timeoutMs,
                    connectorEnabled,
                    forceReject,
                    request.rejectReasonCode(),
                    request.rejectReasonMessage());

            connectorCreated = true;
        } else {
            validateExistingConnectorBank(connectorName, bankCode);
        }

        boolean routingRuleAlreadyExisted = routingRuleExists(routeCode);
        boolean routingRuleCreated = false;

        if (!routingRuleAlreadyExisted) {
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
                    bankCode,
                    messageType,
                    connectorName,
                    priority,
                    routeEnabled);

            routingRuleCreated = true;
        }

        String status = resolveStatus(participantCreated, connectorCreated, routingRuleCreated);
        String message = resolveMessage(status, bankCode, sourceBank, routeCode, connectorName);

        auditOnboarding(
                bankCode,
                bankName,
                connectorName,
                connectorType,
                sourceBank,
                messageType,
                routeCode,
                participantCreated,
                connectorCreated,
                routingRuleCreated,
                status);

        return new OperationsBankOnboardingResponse(
                status,
                LocalDateTime.now(),

                bankCode,
                bankName,

                connectorName,
                connectorType,

                sourceBank,
                bankCode,
                messageType,
                routeCode,

                participantCreated,
                participantAlreadyExisted,

                connectorCreated,
                connectorAlreadyExisted,

                routingRuleCreated,
                routingRuleAlreadyExisted,

                "Run route resolve test: GET /api/routing-rules/resolve?sourceBank="
                        + sourceBank
                        + "&destinationBank="
                        + bankCode
                        + "&messageType="
                        + messageType,
                message);
    }

    private boolean participantExists(String bankCode) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM participants WHERE bank_code = ?",
                Long.class,
                bankCode);

        return count != null && count > 0;
    }

    private boolean connectorExists(String connectorName) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM connector_configs WHERE connector_name = ?",
                Long.class,
                connectorName);

        return count != null && count > 0;
    }

    private boolean routingRuleExists(String routeCode) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM routing_rules WHERE route_code = ?",
                Long.class,
                routeCode);

        return count != null && count > 0;
    }

    private void validateExistingConnectorBank(String connectorName, String expectedBankCode) {
        String actualBankCode = jdbcTemplate.queryForObject(
                "SELECT bank_code FROM connector_configs WHERE connector_name = ?",
                String.class,
                connectorName);

        if (!expectedBankCode.equals(actualBankCode)) {
            throw new IllegalArgumentException(
                    "connectorName already exists but is mapped to another bank. connectorName="
                            + connectorName
                            + ", expectedBankCode="
                            + expectedBankCode
                            + ", actualBankCode="
                            + actualBankCode);
        }
    }

    private String requiredText(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(fieldName + " is required");
        }

        return value.trim();
    }

    private String requiredUpper(String value, String fieldName) {
        return requiredText(value, fieldName).toUpperCase();
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

    private void validateConnectorType(String connectorType) {
        if (!ALLOWED_CONNECTOR_TYPES.contains(connectorType)) {
            throw new IllegalArgumentException("connectorType must be one of " + ALLOWED_CONNECTOR_TYPES);
        }
    }

    private void validateMessageType(String messageType) {
        if (!ALLOWED_MESSAGE_TYPES.contains(messageType)) {
            throw new IllegalArgumentException("messageType must be one of " + ALLOWED_MESSAGE_TYPES);
        }
    }

    private String resolveStatus(
            boolean participantCreated,
            boolean connectorCreated,
            boolean routingRuleCreated) {
        if (participantCreated && connectorCreated && routingRuleCreated) {
            return "ONBOARDED";
        }

        if (participantCreated || connectorCreated || routingRuleCreated) {
            return "PARTIALLY_ONBOARDED";
        }

        return "ALREADY_ONBOARDED";
    }

    private String resolveMessage(
            String status,
            String bankCode,
            String sourceBank,
            String routeCode,
            String connectorName) {
        if ("ONBOARDED".equals(status)) {
            return "Bank onboarded successfully";
        }

        if ("PARTIALLY_ONBOARDED".equals(status)) {
            return "Some onboarding resources were created and some already existed";
        }

        return "Bank already onboarded. bankCode="
                + bankCode
                + ", sourceBank="
                + sourceBank
                + ", routeCode="
                + routeCode
                + ", connectorName="
                + connectorName;
    }

    private void auditOnboarding(
            String bankCode,
            String bankName,
            String connectorName,
            String connectorType,
            String sourceBank,
            String messageType,
            String routeCode,
            boolean participantCreated,
            boolean connectorCreated,
            boolean routingRuleCreated,
            String status) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("bankCode", bankCode);
            payload.put("bankName", bankName);
            payload.put("connectorName", connectorName);
            payload.put("connectorType", connectorType);
            payload.put("sourceBank", sourceBank);
            payload.put("destinationBank", bankCode);
            payload.put("messageType", messageType);
            payload.put("routeCode", routeCode);
            payload.put("participantCreated", participantCreated);
            payload.put("connectorCreated", connectorCreated);
            payload.put("routingRuleCreated", routingRuleCreated);
            payload.put("status", status);

            auditLogService.log(
                    "BANK_ONBOARDING_COMPLETED",
                    ENTITY_TYPE,
                    bankCode,
                    ACTOR,
                    payload);
        } catch (Exception ignored) {
            /*
             * Do not fail onboarding only because audit logging failed.
             */
        }
    }
}