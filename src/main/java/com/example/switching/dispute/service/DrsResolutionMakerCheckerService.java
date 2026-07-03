package com.example.switching.dispute.service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.example.switching.audit.service.AuditLogService;
import com.example.switching.dispute.dto.DisputeResponse;
import com.example.switching.dispute.dto.DrsResolutionSubmitRequest;
import com.example.switching.dispute.exception.DisputeNotAuthorizedException;
import com.example.switching.dispute.exception.DisputeNotFoundException;
import com.example.switching.webhook.service.WebhookEventPublisher;

@Service
public class DrsResolutionMakerCheckerService {

    private static final Set<String> SUBMITTABLE_STATUSES = Set.of("OPEN", "UNDER_REVIEW", "ESCALATED");
    private static final Set<String> VALID_DECISIONS = Set.of(
            "NO_ACTION",
            "REFUND_REQUIRED",
            "MANUAL_ADJUSTMENT_REQUIRED");

    private final JdbcTemplate jdbc;
    private final AuditLogService auditLogService;
    private final WebhookEventPublisher webhookPublisher;
    private final DisputeAutoRefundService autoRefundService;

    public DrsResolutionMakerCheckerService(
            JdbcTemplate jdbc,
            AuditLogService auditLogService,
            WebhookEventPublisher webhookPublisher,
            DisputeAutoRefundService autoRefundService) {
        this.jdbc = jdbc;
        this.auditLogService = auditLogService;
        this.webhookPublisher = webhookPublisher;
        this.autoRefundService = autoRefundService;
    }

    @Transactional
    public DisputeResponse submitResolution(Long disputeId, DrsResolutionSubmitRequest request, String maker) {
        Map<String, Object> dispute = load(disputeId);
        String currentStatus = string(dispute.get("status"));
        String decision = request.decision().toUpperCase();

        if (!SUBMITTABLE_STATUSES.contains(currentStatus)) {
            throw new IllegalStateException("Dispute " + disputeId
                    + " cannot be submitted for approval from status " + currentStatus);
        }
        if (!VALID_DECISIONS.contains(decision)) {
            throw new IllegalArgumentException("Invalid DRS resolution decision: " + request.decision());
        }

        LocalDateTime now = LocalDateTime.now();
        String evidence = mergeEvidence(string(dispute.get("evidence")), request.evidence(), maker, decision, now);
        jdbc.update(
                """
                UPDATE disputes
                   SET status = 'PENDING_APPROVAL',
                       proposed_resolution = ?,
                       proposed_note = ?,
                       proposed_by = ?,
                       proposed_at = ?,
                       evidence = ?,
                       updated_at = ?
                 WHERE dispute_id = ?
                """,
                decision,
                request.note(),
                maker,
                now,
                evidence,
                now,
                disputeId);

        audit(dispute, "DRS_RESOLUTION_SUBMITTED", maker, Map.of(
                "decision", decision,
                "note", request.note(),
                "nextAction", "CHECKER_APPROVAL_REQUIRED"));
        publish(dispute, "PENDING_APPROVAL");
        return loadResponse(disputeId);
    }

    @Transactional
    public DisputeResponse approveResolution(Long disputeId, String checker, String note) {
        Map<String, Object> dispute = load(disputeId);
        requirePendingApproval(disputeId, dispute);

        String maker = string(dispute.get("proposed_by"));
        if (StringUtils.hasText(maker) && maker.equals(checker)) {
            throw new DisputeNotAuthorizedException(disputeId);
        }

        String decision = string(dispute.get("proposed_resolution"));
        String finalStatus = switch (decision) {
            case "NO_ACTION" -> "RESOLVED_NO_ACTION";
            case "REFUND_REQUIRED" -> "RESOLVED_REFUND";
            case "MANUAL_ADJUSTMENT_REQUIRED" -> "ESCALATED";
            default -> throw new IllegalStateException("Missing proposed resolution for dispute " + disputeId);
        };

        LocalDateTime now = LocalDateTime.now();
        jdbc.update(
                """
                UPDATE disputes
                   SET status = ?,
                       resolved_at = CASE WHEN ? IN ('RESOLVED_NO_ACTION','RESOLVED_REFUND') THEN ? ELSE resolved_at END,
                       resolution_note = ?,
                       checked_by = ?,
                       checked_at = ?,
                       checker_note = ?,
                       updated_at = ?
                 WHERE dispute_id = ?
                """,
                finalStatus,
                finalStatus,
                now,
                buildResolutionNote(decision, note),
                checker,
                now,
                note,
                now,
                disputeId);

        applyTransferDecision(dispute, decision, note);
        if ("REFUND_REQUIRED".equals(decision)) {
            DisputeAutoRefundService.RefundExecutionResult refund = autoRefundService.initiateRefund(disputeId);
            audit(dispute, "DRS_REFUND_COMPLETED", checker, Map.of(
                    "decision", decision,
                    "finalStatus", finalStatus,
                    "refundId", refund.refundId(),
                    "refundRef", refund.refundRef(),
                    "refundAmount", refund.amount().toPlainString(),
                    "refundStatus", refund.status(),
                    "debitedPspId", refund.debitedPspId(),
                    "creditedPspId", refund.creditedPspId(),
                    "completedAt", refund.completedAt().toString(),
                    "note", nullToEmpty(note)));
        }
        audit(dispute, "DRS_RESOLUTION_APPROVED", checker, Map.of(
                "decision", decision,
                "finalStatus", finalStatus,
                "maker", maker,
                "checker", checker,
                "note", nullToEmpty(note)));
        publish(dispute, finalStatus);
        return loadResponse(disputeId);
    }

    @Transactional
    public DisputeResponse rejectResolution(Long disputeId, String checker, String note) {
        Map<String, Object> dispute = load(disputeId);
        requirePendingApproval(disputeId, dispute);

        String maker = string(dispute.get("proposed_by"));
        if (StringUtils.hasText(maker) && maker.equals(checker)) {
            throw new DisputeNotAuthorizedException(disputeId);
        }

        LocalDateTime now = LocalDateTime.now();
        jdbc.update(
                """
                UPDATE disputes
                   SET status = 'UNDER_REVIEW',
                       checked_by = ?,
                       checked_at = ?,
                       checker_note = ?,
                       updated_at = ?
                 WHERE dispute_id = ?
                """,
                checker,
                now,
                note,
                now,
                disputeId);

        audit(dispute, "DRS_RESOLUTION_REJECTED", checker, Map.of(
                "maker", maker,
                "checker", checker,
                "note", nullToEmpty(note),
                "nextAction", "MAKER_REVIEW_REQUIRED"));
        publish(dispute, "UNDER_REVIEW");
        return loadResponse(disputeId);
    }

    private void applyTransferDecision(Map<String, Object> dispute, String decision, String note) {
        String txnRef = string(dispute.get("txn_ref"));
        if ("NO_ACTION".equals(decision)) {
            jdbc.update(
                    """
                    UPDATE transactions
                       SET confirmation_status = 'CONFIRMED',
                           settlement_confidence = 'CONFIRMED',
                           reference = ?,
                           updated_at = NOW()
                     WHERE transaction_ref = ?
                       AND status = 'SETTLED'
                    """,
                    "DRS resolved no action: " + nullToEmpty(note),
                    txnRef);
        } else {
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
                    "DRS approved " + decision + ": " + nullToEmpty(note),
                    txnRef);
        }
    }

    private void requirePendingApproval(Long disputeId, Map<String, Object> dispute) {
        if (!"PENDING_APPROVAL".equals(string(dispute.get("status")))) {
            throw new IllegalStateException("Dispute " + disputeId + " is not PENDING_APPROVAL");
        }
    }

    private Map<String, Object> load(Long disputeId) {
        try {
            return jdbc.queryForMap(
                    """
                    SELECT dispute_id, txn_ref, raising_psp_id, responding_psp_id, dispute_type,
                           status, raised_at, sla_deadline, resolved_at, evidence, resolution_note,
                           auto_ruled, proposed_resolution, proposed_note, proposed_by, proposed_at,
                           checked_by, checked_at, checker_note
                      FROM disputes
                     WHERE dispute_id = ?
                    """,
                    disputeId);
        } catch (EmptyResultDataAccessException ex) {
            throw new DisputeNotFoundException(disputeId);
        }
    }

    private DisputeResponse loadResponse(Long disputeId) {
        Map<String, Object> dispute = load(disputeId);
        return new DisputeResponse(
                longValue(dispute.get("dispute_id")),
                string(dispute.get("txn_ref")),
                string(dispute.get("raising_psp_id")),
                string(dispute.get("responding_psp_id")),
                string(dispute.get("dispute_type")),
                string(dispute.get("status")),
                time(dispute.get("raised_at")),
                time(dispute.get("sla_deadline")),
                time(dispute.get("resolved_at")),
                string(dispute.get("evidence")),
                string(dispute.get("resolution_note")),
                Boolean.TRUE.equals(dispute.get("auto_ruled")));
    }

    private void audit(Map<String, Object> dispute, String eventType, String actor, Map<String, Object> details) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("disputeId", dispute.get("dispute_id"));
        payload.put("transferRef", dispute.get("txn_ref"));
        payload.put("disputeType", dispute.get("dispute_type"));
        payload.putAll(details);
        auditLogService.log(eventType, "DISPUTE", string(dispute.get("txn_ref")), actor, payload);
    }

    private void publish(Map<String, Object> dispute, String status) {
        Map<String, Object> payload = Map.of(
                "disputeId", dispute.get("dispute_id"),
                "txnRef", dispute.get("txn_ref"),
                "status", status,
                "disputeType", dispute.get("dispute_type"));
        webhookPublisher.publishQuietly("DISPUTE.STATUS_CHANGED",
                string(dispute.get("raising_psp_id")), string(dispute.get("txn_ref")), payload);
        webhookPublisher.publishQuietly("DISPUTE.STATUS_CHANGED",
                string(dispute.get("responding_psp_id")), string(dispute.get("txn_ref")), payload);
    }

    private String mergeEvidence(String existing, String additional, String maker, String decision, LocalDateTime now) {
        if (!StringUtils.hasText(additional)) {
            return StringUtils.hasText(existing) ? existing : "[]";
        }
        String safeExisting = StringUtils.hasText(existing) ? existing : "[]";
        String escaped = additional.replace("\\", "\\\\").replace("\"", "\\\"");
        String entry = "{\"type\":\"MAKER_RESOLUTION_EVIDENCE\",\"maker\":\"" + maker
                + "\",\"decision\":\"" + decision + "\",\"createdAt\":\"" + now
                + "\",\"evidence\":\"" + escaped + "\"}";
        if ("[]".equals(safeExisting.trim())) {
            return "[" + entry + "]";
        }
        if (safeExisting.trim().endsWith("]")) {
            return safeExisting.trim().substring(0, safeExisting.trim().length() - 1) + "," + entry + "]";
        }
        return safeExisting;
    }

    private String buildResolutionNote(String decision, String note) {
        return "Checker approved " + decision + (StringUtils.hasText(note) ? ": " + note : "");
    }

    private Long longValue(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private LocalDateTime time(Object value) {
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        return null;
    }

    private String string(Object value) {
        return value == null ? "" : value.toString();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
