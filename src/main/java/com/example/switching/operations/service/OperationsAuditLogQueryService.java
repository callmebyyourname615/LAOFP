package com.example.switching.operations.service;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.example.switching.common.util.MaskingUtil;
import com.example.switching.operations.dto.OperationsAuditLogItemResponse;
import com.example.switching.operations.dto.OperationsAuditLogListResponse;

@Service
public class OperationsAuditLogQueryService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private final JdbcTemplate jdbcTemplate;

    public OperationsAuditLogQueryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public OperationsAuditLogListResponse searchAuditLogs(
            String eventType,
            String referenceType,
            String referenceId,
            String actor,
            String transferRef,
            String requestId,
            String messageId,
            String fromDate,
            String toDate,
            Boolean includePayload,
            Integer requestedLimit,
            Integer requestedOffset
    ) {
        int limit = normalizeLimit(requestedLimit);
        int offset = normalizeOffset(requestedOffset);
        boolean shouldIncludePayload = includePayload != null && includePayload;

        List<Object> params = new ArrayList<>();

        String whereClause = buildWhereClause(
                eventType,
                referenceType,
                referenceId,
                actor,
                transferRef,
                requestId,
                messageId,
                fromDate,
                toDate,
                params
        );

        Long totalItems = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs a " + whereClause,
                Long.class,
                params.toArray()
        );

        List<Object> listParams = new ArrayList<>(params);
        listParams.add(limit);
        listParams.add(offset);

        String payloadSelect = shouldIncludePayload
                ? "a.payload"
                : "NULL AS payload";

        String sql = """
                SELECT
                    a.id,
                    a.event_type,
                    a.reference_type,
                    a.reference_id,
                    a.actor,
                    a.created_at,
                """
                + payloadSelect
                + """
                
                FROM audit_logs a
                """
                + whereClause
                + """
                
                ORDER BY a.created_at DESC, a.id DESC
                LIMIT ? OFFSET ?
                """;

        List<OperationsAuditLogItemResponse> items = jdbcTemplate.query(
                sql,
                (rs, rowNum) -> mapRow(rs),
                listParams.toArray()
        );

        return new OperationsAuditLogListResponse(
                items.isEmpty() ? "EMPTY" : "HAS_AUDIT_LOGS",
                LocalDateTime.now(),
                totalItems == null ? 0L : totalItems,
                items.size(),
                limit,
                offset,
                shouldIncludePayload,
                items
        );
    }

    private String buildWhereClause(
            String eventType,
            String referenceType,
            String referenceId,
            String actor,
            String transferRef,
            String requestId,
            String messageId,
            String fromDate,
            String toDate,
            List<Object> params
    ) {
        List<String> conditions = new ArrayList<>();

        if (StringUtils.hasText(eventType)) {
            conditions.add("a.event_type = ?");
            params.add(eventType.trim().toUpperCase());
        }

        if (StringUtils.hasText(referenceType)) {
            conditions.add("a.reference_type = ?");
            params.add(referenceType.trim().toUpperCase());
        }

        if (StringUtils.hasText(referenceId)) {
            conditions.add("a.reference_id = ?");
            params.add(referenceId.trim());
        }

        if (StringUtils.hasText(actor)) {
            conditions.add("a.actor = ?");
            params.add(actor.trim().toUpperCase());
        }

        if (StringUtils.hasText(transferRef)) {
            conditions.add("(a.reference_id = ? OR a.payload LIKE ?)");
            params.add(transferRef.trim());
            params.add("%" + transferRef.trim() + "%");
        }

        if (StringUtils.hasText(requestId)) {
            conditions.add("a.payload LIKE ?");
            params.add("%" + requestId.trim() + "%");
        }

        if (StringUtils.hasText(messageId)) {
            conditions.add("a.payload LIKE ?");
            params.add("%" + messageId.trim() + "%");
        }

        if (StringUtils.hasText(fromDate)) {
            LocalDate parsedFromDate = LocalDate.parse(fromDate.trim());
            conditions.add("a.created_at >= ?");
            params.add(parsedFromDate.atStartOfDay());
        }

        if (StringUtils.hasText(toDate)) {
            LocalDate parsedToDate = LocalDate.parse(toDate.trim());
            conditions.add("a.created_at < ?");
            params.add(parsedToDate.plusDays(1).atStartOfDay());
        }

        if (conditions.isEmpty()) {
            return "";
        }

        return " WHERE " + String.join(" AND ", conditions);
    }

    private OperationsAuditLogItemResponse mapRow(ResultSet rs) throws java.sql.SQLException {
        return new OperationsAuditLogItemResponse(
                rs.getLong("id"),
                rs.getString("event_type"),
                rs.getString("reference_type"),
                rs.getString("reference_id"),
                rs.getString("actor"),
                null,   // channelId — audit_logs table has no channel_id column
                toLocalDateTime(rs.getTimestamp("created_at")),
                MaskingUtil.maskAccountFieldsInText(rs.getString("payload"))
        );
    }

    private int normalizeLimit(Integer requestedLimit) {
        if (requestedLimit == null || requestedLimit <= 0) {
            return DEFAULT_LIMIT;
        }

        return Math.min(requestedLimit, MAX_LIMIT);
    }

    private int normalizeOffset(Integer requestedOffset) {
        if (requestedOffset == null || requestedOffset < 0) {
            return 0;
        }

        return requestedOffset;
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        if (timestamp == null) {
            return null;
        }

        return timestamp.toLocalDateTime();
    }
}
