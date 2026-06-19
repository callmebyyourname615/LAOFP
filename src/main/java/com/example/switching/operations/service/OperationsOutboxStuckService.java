package com.example.switching.operations.service;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.example.switching.operations.dto.OperationsOutboxStuckItemResponse;
import com.example.switching.operations.dto.OperationsOutboxStuckListResponse;

@Service
public class OperationsOutboxStuckService {

    private static final int DEFAULT_THRESHOLD_MINUTES = 5;
    private static final int MAX_THRESHOLD_MINUTES = 1440;
    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private final JdbcTemplate jdbcTemplate;

    public OperationsOutboxStuckService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public OperationsOutboxStuckListResponse getStuckOutboxEvents(
            Integer requestedMinutes,
            Integer requestedLimit
    ) {
        int thresholdMinutes = normalizeMinutes(requestedMinutes);
        int limit = normalizeLimit(requestedLimit);

        List<OperationsOutboxStuckItemResponse> items = jdbcTemplate.query(
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
                    processed_at,
                    next_retry_at,
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

        String status = items.isEmpty() ? "EMPTY" : "HAS_STUCK_OUTBOX";

        return new OperationsOutboxStuckListResponse(
                status,
                LocalDateTime.now(),
                thresholdMinutes,
                items.size(),
                items
        );
    }

    private OperationsOutboxStuckItemResponse mapRow(ResultSet rs) throws java.sql.SQLException {
        Long stuckMinutes = rs.getLong("stuck_minutes");

        return new OperationsOutboxStuckItemResponse(
                rs.getLong("id"),
                rs.getString("transaction_ref"),
                rs.getString("message_type"),
                rs.getString("status"),
                rs.getInt("retry_count"),
                rs.getString("last_error"),
                toLocalDateTime(rs.getTimestamp("created_at")),
                toLocalDateTime(rs.getTimestamp("updated_at")),
                toLocalDateTime(rs.getTimestamp("processed_at")),
                toLocalDateTime(rs.getTimestamp("next_retry_at")),
                stuckMinutes,
                true,
                "Review worker logs, then recover or requeue this PROCESSING outbox event"
        );
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

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        if (timestamp == null) {
            return null;
        }

        return timestamp.toLocalDateTime();
    }
}