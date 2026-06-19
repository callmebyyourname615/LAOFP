package com.example.switching.operations.service;

import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.example.switching.audit.service.AuditLogService;
import com.example.switching.common.util.AuditActorUtil;
import com.example.switching.operations.dto.OperationsOutboxMarkReviewedRequest;
import com.example.switching.operations.dto.OperationsOutboxMarkReviewedResponse;

@Service
public class OperationsOutboxMarkReviewedService {

    private static final String ENTITY_TYPE = "TRANSFER";

    private final JdbcTemplate jdbcTemplate;
    private final AuditLogService auditLogService;

    public OperationsOutboxMarkReviewedService(
            JdbcTemplate jdbcTemplate,
            AuditLogService auditLogService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public OperationsOutboxMarkReviewedResponse markReviewed(
            Long outboxEventId,
            OperationsOutboxMarkReviewedRequest request
    ) {
        if (outboxEventId == null || outboxEventId <= 0) {
            throw new IllegalArgumentException("outboxEventId must be greater than 0");
        }

        OutboxRow row = findOutbox(outboxEventId);

        if (!"FAILED".equalsIgnoreCase(row.status())) {
            throw new IllegalStateException(
                    "Only FAILED outbox events can be marked as REVIEWED. currentStatus=" + row.status()
            );
        }

        String reason = StringUtils.hasText(request.reason())
                ? request.reason().trim()
                : "Marked as reviewed by operator";

        String reviewedBy = StringUtils.hasText(request.reviewedBy())
                ? request.reviewedBy().trim()
                : "OPERATOR";

        int updated = jdbcTemplate.update(
                """
                UPDATE outbox_messages
                SET
                    status = 'REVIEWED',
                    last_error = NULL,
                    next_retry_at = NULL,
                    processed_at = NOW(),
                    updated_at = NOW()
                WHERE id = ?
                  AND status = 'FAILED'
                """,
                outboxEventId
        );

        if (updated != 1) {
            throw new IllegalStateException(
                    "Outbox event was not marked as reviewed because status changed. outboxEventId=" + outboxEventId
            );
        }

        auditReviewed(row, reason, reviewedBy);

        return new OperationsOutboxMarkReviewedResponse(
                "REVIEWED",
                LocalDateTime.now(),
                row.id(),
                row.transferRef(),
                row.status(),
                "REVIEWED",
                row.retryCount(),
                reason,
                reviewedBy,
                "Outbox event marked as reviewed"
        );
    }

    private OutboxRow findOutbox(Long outboxEventId) {
        return jdbcTemplate.query(
                """
                SELECT
                    id,
                    transaction_ref,
                    message_type,
                    status,
                    retry_count,
                    last_error
                FROM outbox_messages
                WHERE id = ?
                """,
                rs -> {
                    if (!rs.next()) {
                        throw new IllegalArgumentException("Outbox event not found. id=" + outboxEventId);
                    }

                    return mapRow(rs);
                },
                outboxEventId
        );
    }

    private OutboxRow mapRow(ResultSet rs) throws java.sql.SQLException {
        return new OutboxRow(
                rs.getLong("id"),
                rs.getString("transaction_ref"),
                rs.getString("message_type"),
                rs.getString("status"),
                rs.getInt("retry_count"),
                rs.getString("last_error")
        );
    }

    private void auditReviewed(OutboxRow row, String reason, String reviewedBy) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("outboxEventId", row.id());
            payload.put("transferRef", row.transferRef());
            payload.put("messageType", row.messageType());
            payload.put("previousStatus", row.status());
            payload.put("newStatus", "REVIEWED");
            payload.put("retryCount", row.retryCount());
            payload.put("lastError", row.lastError());
            payload.put("reason", reason);
            payload.put("reviewedBy", reviewedBy);
            payload.put("action", "MARK_OUTBOX_REVIEWED");
            payload.put("reviewedAt", LocalDateTime.now().toString());

            auditLogService.log(
                    "OUTBOX_EVENT_MARKED_REVIEWED",
                    ENTITY_TYPE,
                    row.transferRef(),
                    AuditActorUtil.currentActor(),
                    payload
            );
        } catch (Exception ignored) {
            /*
             * Do not rollback review state because audit logging failed.
             */
        }
    }

    private record OutboxRow(
            Long id,
            String transferRef,
            String messageType,
            String status,
            Integer retryCount,
            String lastError
    ) {
    }
}