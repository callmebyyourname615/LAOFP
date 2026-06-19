package com.example.switching.operations.service;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.example.switching.operations.dto.OperationsOutboxFailureItemResponse;
import com.example.switching.operations.dto.OperationsOutboxFailureListResponse;

@Service
public class OperationsOutboxFailureService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private final JdbcTemplate jdbcTemplate;

    public OperationsOutboxFailureService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public OperationsOutboxFailureListResponse getFailedOutboxEvents(Integer requestedLimit) {
        int limit = normalizeLimit(requestedLimit);

        Long totalFailed = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_messages WHERE status = 'FAILED'",
                Long.class
        );

        List<OperationsOutboxFailureItemResponse> items = jdbcTemplate.query(
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
                    next_retry_at
                FROM outbox_messages
                WHERE status = 'FAILED'
                ORDER BY updated_at DESC, created_at DESC, id DESC
                LIMIT ?
                """,
                (rs, rowNum) -> mapRow(rs),
                limit
        );

        String status = items.isEmpty() ? "EMPTY" : "HAS_FAILURES";

        return new OperationsOutboxFailureListResponse(
                status,
                LocalDateTime.now(),
                totalFailed == null ? 0L : totalFailed,
                items.size(),
                items
        );
    }

    private OperationsOutboxFailureItemResponse mapRow(ResultSet rs) throws java.sql.SQLException {
        Long id = rs.getLong("id");
        String status = rs.getString("status");

        boolean retryable = "FAILED".equalsIgnoreCase(status);

        return new OperationsOutboxFailureItemResponse(
                id,
                rs.getString("transaction_ref"),
                rs.getString("message_type"),
                status,
                rs.getInt("retry_count"),
                rs.getString("last_error"),
                toLocalDateTime(rs.getTimestamp("created_at")),
                toLocalDateTime(rs.getTimestamp("updated_at")),
                toLocalDateTime(rs.getTimestamp("next_retry_at")),
                retryable,
                retryable ? "/api/outbox-events/" + id + "/retry" : null
        );
    }

    private int normalizeLimit(Integer requestedLimit) {
        if (requestedLimit == null) {
            return DEFAULT_LIMIT;
        }

        if (requestedLimit <= 0) {
            return DEFAULT_LIMIT;
        }

        return Math.min(requestedLimit, MAX_LIMIT);
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        if (timestamp == null) {
            return null;
        }

        return timestamp.toLocalDateTime();
    }
}