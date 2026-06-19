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

import com.example.switching.audit.service.AuditLogService;
import com.example.switching.operations.dto.OperationsOutboxFailureRetryAllResponse;
import com.example.switching.operations.dto.OperationsOutboxFailureRetryItemResponse;

@Service
public class OperationsOutboxFailureRetryAllService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;
    private static final String ENTITY_TYPE = "TRANSFER";
    private static final String ACTOR = "API";

    private final JdbcTemplate jdbcTemplate;
    private final AuditLogService auditLogService;

    public OperationsOutboxFailureRetryAllService(
            JdbcTemplate jdbcTemplate,
            AuditLogService auditLogService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public OperationsOutboxFailureRetryAllResponse retryAllFailed(Integer requestedLimit) {
        int limit = normalizeLimit(requestedLimit);

        Long totalFailedBefore = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_messages WHERE status = 'FAILED'",
                Long.class
        );

        List<FailedOutboxRow> failedRows = jdbcTemplate.query(
                """
                SELECT
                    id,
                    transaction_ref,
                    message_type,
                    status,
                    retry_count,
                    last_error
                FROM outbox_messages
                WHERE status = 'FAILED'
                ORDER BY updated_at DESC, created_at DESC, id DESC
                LIMIT ?
                """,
                (rs, rowNum) -> mapFailedRow(rs),
                limit
        );

        List<OperationsOutboxFailureRetryItemResponse> items = new ArrayList<>();

        for (FailedOutboxRow row : failedRows) {
            int updated = jdbcTemplate.update(
                    """
                    UPDATE outbox_messages
                    SET
                        status = 'PENDING',
                        last_error = NULL,
                        next_retry_at = NULL,
                        processed_at = NULL,
                        updated_at = NOW()
                    WHERE id = ?
                      AND status = 'FAILED'
                    """,
                    row.id()
            );

            boolean requeued = updated == 1;

            if (requeued) {
                safeAudit(
                        "OUTBOX_FAILURE_REQUEUED",
                        row.transferRef(),
                        row,
                        "FAILED",
                        "PENDING"
                );
            }

            items.add(new OperationsOutboxFailureRetryItemResponse(
                    row.id(),
                    row.transferRef(),
                    row.messageType(),
                    row.status(),
                    requeued ? "PENDING" : row.status(),
                    row.retryCount(),
                    requeued,
                    requeued
                            ? "Outbox event requeued for worker dispatch"
                            : "Outbox event was not requeued because status changed or event is no longer FAILED"
            ));
        }

        long requeuedCount = items.stream()
                .filter(OperationsOutboxFailureRetryItemResponse::requeued)
                .count();

        String status;
        if (items.isEmpty()) {
            status = "NO_FAILED_OUTBOX";
        } else if (requeuedCount == items.size()) {
            status = "REQUEUED";
        } else if (requeuedCount > 0) {
            status = "PARTIALLY_REQUEUED";
        } else {
            status = "NOT_REQUEUED";
        }

        return new OperationsOutboxFailureRetryAllResponse(
                status,
                LocalDateTime.now(),
                limit,
                totalFailedBefore == null ? 0L : totalFailedBefore,
                (int) requeuedCount,
                items
        );
    }

    private FailedOutboxRow mapFailedRow(ResultSet rs) throws java.sql.SQLException {
        return new FailedOutboxRow(
                rs.getLong("id"),
                rs.getString("transaction_ref"),
                rs.getString("message_type"),
                rs.getString("status"),
                rs.getInt("retry_count"),
                rs.getString("last_error")
        );
    }

    private int normalizeLimit(Integer requestedLimit) {
        if (requestedLimit == null || requestedLimit <= 0) {
            return DEFAULT_LIMIT;
        }

        return Math.min(requestedLimit, MAX_LIMIT);
    }

    private void safeAudit(
            String eventType,
            String transferRef,
            FailedOutboxRow row,
            String previousStatus,
            String newStatus
    ) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("outboxEventId", row.id());
            payload.put("transferRef", row.transferRef());
            payload.put("messageType", row.messageType());
            payload.put("previousStatus", previousStatus);
            payload.put("newStatus", newStatus);
            payload.put("retryCount", row.retryCount());
            payload.put("lastError", row.lastError());
            payload.put("action", "REQUEUE_FAILED_OUTBOX");
            payload.put("processedAt", LocalDateTime.now().toString());

            auditLogService.log(
                    eventType,
                    ENTITY_TYPE,
                    transferRef,
                    ACTOR,
                    payload
            );
        } catch (Exception ignored) {
            /*
             * Do not block operational retry because audit logging failed.
             * The outbox state change is the critical operator action.
             */
        }
    }

    private record FailedOutboxRow(
            Long id,
            String transferRef,
            String messageType,
            String status,
            Integer retryCount,
            String lastError
    ) {
    }
}