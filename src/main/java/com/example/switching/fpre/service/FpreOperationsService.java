package com.example.switching.fpre.service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.example.switching.config.FpreProperties;
import com.example.switching.fpre.dto.FpreHealthResponse;
import com.example.switching.fpre.dto.FpreRetryHistoryItemResponse;
import com.example.switching.fpre.dto.FpreRetryHistoryResponse;
import com.example.switching.fpre.dto.FpreRetryStatusResponse;
import com.example.switching.fpre.dto.FpreTransferItemResponse;
import com.example.switching.fpre.dto.FpreTransferListResponse;
import com.example.switching.fpre.exception.AmbiguousStateException;
import com.example.switching.transfer.exception.TransferNotFoundException;

@Service
public class FpreOperationsService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private final JdbcTemplate jdbcTemplate;
    private final FpreProperties fpre;

    public FpreOperationsService(JdbcTemplate jdbcTemplate, FpreProperties fpre) {
        this.jdbcTemplate = jdbcTemplate;
        this.fpre = fpre;
    }

    @Transactional(readOnly = true)
    public FpreRetryStatusResponse retryStatus(String txnId) {
        Map<String, Object> row = latestOutboxRow(txnId);
        if (row == null) {
            throw new TransferNotFoundException("Transfer not found for txnId: " + txnId);
        }

        int attemptCount = num(row.get("retry_count"));
        boolean willRetry = bool(row.get("will_retry"));
        String failureClass = str(row.get("failure_class"));
        String outboxStatus = str(row.get("outbox_status"));

        if ("AMBIGUOUS".equals(failureClass)
                && "FAILED".equals(outboxStatus)
                && !willRetry
                && attemptCount >= fpre.getRetryAttempts()) {
            throw new AmbiguousStateException("FPRE ambiguous state unresolved for txnId: " + txnId);
        }

        return new FpreRetryStatusResponse(
                txnId,
                str(row.get("transfer_status")),
                outboxStatus,
                attemptCount,
                fpre.getRetryAttempts(),
                ldt(row.get("next_retry_at")),
                failureClass,
                fpre.isAutoReversalEnabled() && !willRetry && attemptCount >= fpre.getRetryAttempts(),
                willRetry,
                str(row.get("error_code")),
                str(row.get("error_message")));
    }

    @Transactional(readOnly = true)
    public FpreRetryHistoryResponse retryHistory(String txnId) {
        List<FpreRetryHistoryItemResponse> items = jdbcTemplate.query("""
                SELECT COALESCE(retry_count, 0) AS attempt,
                       updated_at AS attempted_at,
                       failure_class,
                       status AS outbox_status,
                       last_error
                  FROM outbox_messages
                 WHERE transaction_ref = ?
                   AND COALESCE(retry_count, 0) > 0
                 ORDER BY updated_at ASC, id ASC
                """, (rs, rowNum) -> new FpreRetryHistoryItemResponse(
                rs.getInt("attempt"),
                rs.getTimestamp("attempted_at") == null ? null : rs.getTimestamp("attempted_at").toLocalDateTime(),
                rs.getString("failure_class"),
                rs.getString("outbox_status"),
                rs.getString("last_error"),
                null), txnId);

        if (items.isEmpty() && !transferExists(txnId)) {
            throw new TransferNotFoundException("Transfer not found for txnId: " + txnId);
        }

        return new FpreRetryHistoryResponse(txnId, items.size(), items);
    }

    @Transactional(readOnly = true)
    public FpreTransferListResponse pending(String pspId, Integer limit) {
        return list(pspId, limit, "PENDING");
    }

    @Transactional(readOnly = true)
    public FpreTransferListResponse failed(String pspId, Integer limit) {
        return list(pspId, limit, "FAILED");
    }

    @Transactional(readOnly = true)
    public FpreHealthResponse health() {
        Long queueDepth = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                  FROM outbox_messages
                 WHERE status = 'PENDING'
                   AND (next_retry_at IS NULL OR next_retry_at <= NOW())
                """, Long.class);

        Long retryable = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM outbox_messages
                 WHERE status = 'PENDING' AND will_retry = true
                """, Long.class);

        Long terminal = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM outbox_messages
                 WHERE status = 'FAILED' AND COALESCE(will_retry, false) = false
                """, Long.class);

        Long reversals = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM reversal_log
                 WHERE triggered_at >= NOW() - INTERVAL '30 minutes'
                """, Long.class);

        Long suspended = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM participants
                 WHERE status = 'INBOUND_SUSPENDED'
                """, Long.class);

        Long success = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM outbox_messages WHERE status = 'SUCCESS'
                """, Long.class);
        Long failed = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM outbox_messages WHERE status = 'FAILED'
                """, Long.class);

        long denominator = safe(success) + safe(failed);
        double retrySuccessRate = denominator == 0 ? 0.0 : (double) safe(success) / denominator;

        Double avgResolutionMs = jdbcTemplate.queryForObject("""
                SELECT COALESCE(AVG(EXTRACT(EPOCH FROM (updated_at - created_at)) * 1000), 0)
                  FROM outbox_messages
                 WHERE status IN ('SUCCESS', 'FAILED')
                """, Double.class);

        return new FpreHealthResponse(
                safe(queueDepth),
                safe(retryable),
                safe(terminal),
                safe(reversals),
                safe(suspended),
                retrySuccessRate,
                avgResolutionMs == null ? 0.0 : avgResolutionMs);
    }

    private FpreTransferListResponse list(String pspId, Integer limit, String outboxStatus) {
        int resolvedLimit = resolveLimit(limit);
        String normalizedPspId = normalize(pspId);

        List<FpreTransferItemResponse> items = jdbcTemplate.query("""
                SELECT t.transaction_ref,
                       t.status AS transfer_status,
                       t.source_bank,
                       t.destination_bank,
                       t.amount,
                       t.currency,
                       t.error_code,
                       t.error_message,
                       o.failure_class,
                       o.will_retry,
                       o.retry_count,
                       o.next_retry_at
                 FROM transactions t
                 JOIN outbox_messages o ON o.transaction_ref = t.transaction_ref
                 WHERE o.status = ?
                   AND (?::varchar IS NULL OR t.destination_bank = ?)
                 ORDER BY COALESCE(o.next_retry_at, o.created_at) ASC, o.id ASC
                 LIMIT ?
                """, (rs, rowNum) -> new FpreTransferItemResponse(
                rs.getString("transaction_ref"),
                rs.getString("transfer_status"),
                rs.getString("source_bank"),
                rs.getString("destination_bank"),
                rs.getBigDecimal("amount"),
                rs.getString("currency"),
                rs.getString("failure_class"),
                rs.getObject("will_retry", Boolean.class),
                rs.getObject("retry_count", Integer.class),
                rs.getTimestamp("next_retry_at") == null ? null : rs.getTimestamp("next_retry_at").toLocalDateTime(),
                rs.getString("error_code"),
                rs.getString("error_message")),
                outboxStatus, normalizedPspId, normalizedPspId, resolvedLimit);

        return new FpreTransferListResponse(items.size(), resolvedLimit, normalizedPspId, items);
    }

    private Map<String, Object> latestOutboxRow(String txnId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT t.transaction_ref,
                       t.status AS transfer_status,
                       t.error_code,
                       t.error_message,
                       o.status AS outbox_status,
                       o.retry_count,
                       o.next_retry_at,
                       o.failure_class,
                       o.will_retry
                  FROM transactions t
                  LEFT JOIN outbox_messages o ON o.transaction_ref = t.transaction_ref
                 WHERE t.transaction_ref = ?
                 ORDER BY o.id DESC NULLS LAST
                 LIMIT 1
                """, txnId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private boolean transferExists(String txnId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE transaction_ref = ?",
                Long.class,
                txnId);
        return count != null && count > 0;
    }

    private int resolveLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private int num(Object value) {
        return value instanceof Number n ? n.intValue() : 0;
    }

    private boolean bool(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof Number n) {
            return n.intValue() != 0;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private LocalDateTime ldt(Object value) {
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        return null;
    }

    private long safe(Long value) {
        return value == null ? 0L : value;
    }
}
