package com.example.switching.dispute.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.example.switching.audit.service.AuditLogService;
import com.example.switching.dispute.config.DisputeProperties;
import com.example.switching.dispute.dto.DisputeRaiseResponse;
import com.example.switching.dispute.dto.PostSettlementDisputeRequest;
import com.example.switching.dispute.exception.DisputeAlreadyExistsException;
import com.example.switching.dispute.exception.DisputeNotFoundException;
import com.example.switching.dispute.exception.DisputeWindowExpiredException;
import com.example.switching.webhook.service.WebhookEventPublisher;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class PostSettlementDisputeService {

    public static final String DISPUTE_TYPE = "POST_SETTLEMENT_DESTINATION_DISPUTE";

    private final JdbcTemplate jdbc;
    private final DisputeProperties props;
    private final AuditLogService auditLogService;
    private final WebhookEventPublisher webhookPublisher;
    private final ObjectMapper objectMapper;

    public PostSettlementDisputeService(
            JdbcTemplate jdbc,
            DisputeProperties props,
            AuditLogService auditLogService,
            WebhookEventPublisher webhookPublisher,
            ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.props = props;
        this.auditLogService = auditLogService;
        this.webhookPublisher = webhookPublisher;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public DisputeRaiseResponse raise(PostSettlementDisputeRequest request, String actor) {
        Map<String, Object> transfer = loadSettledTransfer(request.transferRef());

        LocalDateTime createdAt = timestamp(transfer.get("created_at"));
        if (LocalDateTime.now().isAfter(createdAt.plusDays(props.getWindowDays()))) {
            throw new DisputeWindowExpiredException(request.transferRef());
        }

        Integer existing = jdbc.queryForObject(
                """
                SELECT COUNT(*)
                  FROM disputes
                 WHERE txn_ref = ?
                   AND status IN ('OPEN','UNDER_REVIEW','ESCALATED')
                """,
                Integer.class,
                request.transferRef());
        if (existing != null && existing > 0) {
            throw new DisputeAlreadyExistsException(request.transferRef());
        }

        String sourceBank = string(transfer.get("source_bank"));
        String destinationBank = string(transfer.get("destination_bank"));
        String raisingPspId = request.raisingPspId();
        String respondingPspId = sourceBank.equals(raisingPspId) ? destinationBank : sourceBank;
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime slaDeadline = now.plusDays(props.slaDeadlineDays(DISPUTE_TYPE));
        String evidence = buildEvidence(request, transfer, actor, now);

        Long disputeId = jdbc.queryForObject(
                """
                INSERT INTO disputes
                    (txn_ref, raising_psp_id, responding_psp_id, dispute_type, status,
                     raised_at, sla_deadline, evidence, resolution_note, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'OPEN', ?, ?, ?, ?, ?, ?)
                RETURNING dispute_id
                """,
                Long.class,
                request.transferRef(),
                raisingPspId,
                respondingPspId,
                DISPUTE_TYPE,
                now,
                slaDeadline,
                evidence,
                "Post-settlement destination dispute opened after STGS/RTGS settlement confirmation",
                now,
                now);

        jdbc.update(
                """
                UPDATE transactions
                   SET confirmation_status = 'DISPUTED',
                       settlement_confidence = 'DISPUTED',
                       reference = ?,
                       updated_at = NOW()
                 WHERE transaction_ref = ?
                   AND status = 'SETTLED'
                """,
                "Post-settlement dispute opened: " + nullToDefault(request.reasonCode(), DISPUTE_TYPE),
                request.transferRef());

        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("disputeId", disputeId);
        auditPayload.put("transferRef", request.transferRef());
        auditPayload.put("raisingPspId", raisingPspId);
        auditPayload.put("respondingPspId", respondingPspId);
        auditPayload.put("disputeType", DISPUTE_TYPE);
        auditPayload.put("reasonCode", nullToDefault(request.reasonCode(), ""));
        auditPayload.put("reason", nullToDefault(request.reason(), ""));
        auditPayload.put("nextAction", "DRS_REVIEW_REQUIRED");
        auditLogService.log(
                "POST_SETTLEMENT_DESTINATION_DISPUTE_OPENED",
                "TRANSFER",
                request.transferRef(),
                StringUtils.hasText(actor) ? actor : "OPS",
                auditPayload);

        Map<String, Object> webhookPayload = Map.of(
                "disputeId", disputeId,
                "txnRef", request.transferRef(),
                "status", "OPEN",
                "disputeType", DISPUTE_TYPE,
                "slaDeadline", slaDeadline.toString(),
                "nextAction", "DRS_REVIEW_REQUIRED");
        webhookPublisher.publishQuietly("DISPUTE.STATUS_CHANGED", raisingPspId, request.transferRef(), webhookPayload);
        webhookPublisher.publishQuietly("DISPUTE.STATUS_CHANGED", respondingPspId, request.transferRef(), webhookPayload);

        return new DisputeRaiseResponse(disputeId, request.transferRef(), "OPEN", DISPUTE_TYPE, slaDeadline, now);
    }

    private Map<String, Object> loadSettledTransfer(String transferRef) {
        try {
            return jdbc.queryForMap(
                    """
                    SELECT transaction_ref, source_bank, destination_bank, amount, currency,
                           status, confirmation_status, settlement_confidence,
                           external_reference, created_at, settled_at
                      FROM transactions
                     WHERE transaction_ref = ?
                       AND status = 'SETTLED'
                     LIMIT 1
                    """,
                    transferRef);
        } catch (EmptyResultDataAccessException ex) {
            throw new DisputeNotFoundException(-1L);
        }
    }

    private String buildEvidence(PostSettlementDisputeRequest request, Map<String, Object> transfer,
            String actor, LocalDateTime raisedAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("reasonCode", nullToDefault(request.reasonCode(), DISPUTE_TYPE));
        payload.put("reason", nullToDefault(request.reason(), ""));
        payload.put("rawEvidence", nullToDefault(request.evidence(), ""));
        payload.put("actor", nullToDefault(actor, "OPS"));
        payload.put("raisedAt", raisedAt.toString());
        payload.put("settledAt", string(transfer.get("settled_at")));
        payload.put("amount", money(transfer.get("amount")));
        payload.put("currency", string(transfer.get("currency")));
        payload.put("externalReference", string(transfer.get("external_reference")));
        try {
            return objectMapper.writeValueAsString(java.util.List.of(payload));
        } catch (JsonProcessingException ex) {
            return "[{\"reason\":\"POST_SETTLEMENT_DESTINATION_DISPUTE\"}]";
        }
    }

    private LocalDateTime timestamp(Object value) {
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        return LocalDateTime.now();
    }

    private String money(Object value) {
        if (value instanceof BigDecimal amount) {
            return amount.toPlainString();
        }
        return string(value);
    }

    private String string(Object value) {
        return value == null ? "" : value.toString();
    }

    private String nullToDefault(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }
}
