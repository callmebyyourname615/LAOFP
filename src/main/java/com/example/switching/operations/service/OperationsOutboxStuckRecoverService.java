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
import com.example.switching.operations.dto.OperationsOutboxStuckRecoverAllResponse;
import com.example.switching.operations.dto.OperationsOutboxStuckRecoverItemResponse;

@Service
public class OperationsOutboxStuckRecoverService {

    private static final int DEFAULT_THRESHOLD_MINUTES = 5;
    private static final int MAX_THRESHOLD_MINUTES = 1440;
    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private static final String ENTITY_TYPE = "TRANSFER";
    private static final String ACTOR = "API";

    private final JdbcTemplate jdbcTemplate;
    private final AuditLogService auditLogService;

    public OperationsOutboxStuckRecoverService(
            JdbcTemplate jdbcTemplate,
            AuditLogService auditLogService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public OperationsOutboxStuckRecoverAllResponse recoverAllStuck(
            Integer requestedMinutes,
            Integer requestedLimit
    ) {
        int thresholdMinutes = normalizeMinutes(requestedMinutes);
        int limit = normalizeLimit(requestedLimit);

        List<StuckOutboxRow> stuckRows = jdbcTemplate.query(
                """
                SELECT
                    id,
                    transaction_ref,
                    message_type,
                    status,
                    retry_count,
                    last_error,
                    FLOOR(EXTRACT(EPOCH FROM (NOW() - COALESCE(updated_at, processed_at, created_at))) / 60) AS stuck_minutes
                FROM outbox_messages
                WHERE status = 'PROCESSING'
                  AND FLOOR(EXTRACT(EPOCH FROM (NOW() - COALESCE(updated_at, processed_at, created_at))) / 60) >= ?
                ORDER BY COALESCE(updated_at, processed_at, created_at) ASC, id ASC
                LIMIT ?
                """,
                (rs, rowNum) -> mapRow(rs),
                thresholdMinutes,
                limit
        );

        List<OperationsOutboxStuckRecoverItemResponse> items = new ArrayList<>();

        for (StuckOutboxRow row : stuckRows) {
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
                      AND status = 'PROCESSING'
                    """,
                    row.id()
            );

            boolean recovered = updated == 1;

            if (recovered) {
                safeAudit(row);
            }

            items.add(new OperationsOutboxStuckRecoverItemResponse(
                    row.id(),
                    row.transferRef(),
                    row.messageType(),
                    row.status(),
                    recovered ? "PENDING" : row.status(),
                    row.retryCount(),
                    row.stuckMinutes(),
                    recovered,
                    recovered
                            ? "Stuck PROCESSING outbox event recovered to PENDING"
                            : "Outbox event was not recovered because status changed"
            ));
        }

        int recoveredCount = (int) items.stream()
                .filter(OperationsOutboxStuckRecoverItemResponse::recovered)
                .count();

        String status;
        if (items.isEmpty()) {
            status = "NO_STUCK_OUTBOX";
        } else if (recoveredCount == items.size()) {
            status = "RECOVERED";
        } else if (recoveredCount > 0) {
            status = "PARTIALLY_RECOVERED";
        } else {
            status = "NOT_RECOVERED";
        }

        return new OperationsOutboxStuckRecoverAllResponse(
                status,
                LocalDateTime.now(),
                thresholdMinutes,
                limit,
                recoveredCount,
                items
        );
    }

    private StuckOutboxRow mapRow(ResultSet rs) throws java.sql.SQLException {
        return new StuckOutboxRow(
                rs.getLong("id"),
                rs.getString("transaction_ref"),
                rs.getString("message_type"),
                rs.getString("status"),
                rs.getInt("retry_count"),
                rs.getString("last_error"),
                rs.getLong("stuck_minutes")
        );
    }

    private void safeAudit(StuckOutboxRow row) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("outboxEventId", row.id());
            payload.put("transferRef", row.transferRef());
            payload.put("messageType", row.messageType());
            payload.put("previousStatus", row.status());
            payload.put("newStatus", "PENDING");
            payload.put("retryCount", row.retryCount());
            payload.put("lastError", row.lastError());
            payload.put("stuckMinutes", row.stuckMinutes());
            payload.put("action", "RECOVER_STUCK_OUTBOX");
            payload.put("processedAt", LocalDateTime.now().toString());

            auditLogService.log(
                    "OUTBOX_STUCK_RECOVERED",
                    ENTITY_TYPE,
                    row.transferRef(),
                    ACTOR,
                    payload
            );
        } catch (Exception ignored) {
            /*
             * Do not block recovery because audit logging failed.
             * Recovery state change is the critical operation.
             */
        }
    }

    private int normalizeMinutes(Integer requestedMinutes) {
        if (requestedMinutes == null || requestedMinutes <= 0) {
            return DEFAULT_THRESHOLD_MINUTES;
        }

        return Math.min(requestedMinutes, MAX_THRESHOLD_MINUTES);
    }

    private int normalizeLimit(Integer requestedLimit) {
        if (requestedLimit == null || requestedLimit <= 0) {
            return DEFAULT_LIMIT;
        }

        return Math.min(requestedLimit, MAX_LIMIT);
    }

    private record StuckOutboxRow(
            Long id,
            String transferRef,
            String messageType,
            String status,
            Integer retryCount,
            String lastError,
            Long stuckMinutes
    ) {
    }
}