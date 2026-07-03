package com.example.switching.dispute.service;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.example.switching.dispute.dto.DrsDashboardSummaryResponse;
import com.example.switching.dispute.dto.DrsDisputeDetailResponse;
import com.example.switching.dispute.dto.DrsDisputeListItemResponse;
import com.example.switching.dispute.dto.DrsDisputeListResponse;
import com.example.switching.dispute.dto.DrsEvidenceReportResponse;
import com.example.switching.dispute.dto.DrsRefundSnapshotResponse;
import com.example.switching.dispute.dto.DrsStatusCountResponse;
import com.example.switching.dispute.dto.DrsTimelineItemResponse;
import com.example.switching.dispute.dto.DrsTimelineResponse;
import com.example.switching.dispute.dto.DrsTransferSnapshotResponse;
import com.example.switching.dispute.exception.DisputeNotFoundException;

@Service
public class DrsOperationsQueryService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private final JdbcTemplate jdbc;

    public DrsOperationsQueryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public DrsDisputeListResponse list(
            String status,
            String bankCode,
            String disputeType,
            LocalDate dateFrom,
            LocalDate dateTo,
            Integer requestedLimit) {
        int limit = safeLimit(requestedLimit);
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        appendFilter(where, args, "d.status", status);
        appendFilter(where, args, "d.dispute_type", disputeType);
        if (StringUtils.hasText(bankCode)) {
            where.append(" AND (d.raising_psp_id = ? OR d.responding_psp_id = ? OR t.source_bank = ? OR t.destination_bank = ?)");
            args.add(bankCode);
            args.add(bankCode);
            args.add(bankCode);
            args.add(bankCode);
        }
        if (dateFrom != null) {
            where.append(" AND d.raised_at >= ?");
            args.add(dateFrom.atStartOfDay());
        }
        if (dateTo != null) {
            where.append(" AND d.raised_at < ?");
            args.add(dateTo.plusDays(1).atStartOfDay());
        }

        String sql = baseDisputeSelect() + where + " ORDER BY d.raised_at DESC, d.dispute_id DESC LIMIT ?";
        args.add(limit);
        List<DrsDisputeListItemResponse> items = jdbc.query(sql, this::mapListItem, args.toArray());
        return new DrsDisputeListResponse(
                items.size(),
                limit,
                emptyToNull(status),
                emptyToNull(bankCode),
                emptyToNull(disputeType),
                dateFrom == null ? null : dateFrom.toString(),
                dateTo == null ? null : dateTo.toString(),
                items);
    }

    public DrsDisputeDetailResponse detail(Long disputeId) {
        try {
            DrsDisputeListItemResponse dispute = jdbc.queryForObject(
                    baseDisputeSelect() + " WHERE d.dispute_id = ?",
                    this::mapListItem,
                    disputeId);
            DrsTransferSnapshotResponse transfer = new DrsTransferSnapshotResponse(
                    dispute.txnRef(),
                    dispute.sourceBank(),
                    dispute.destinationBank(),
                    dispute.amount(),
                    dispute.currency(),
                    dispute.transferStatus(),
                    dispute.transferStatus(),
                    dispute.confirmationStatus(),
                    dispute.settlementConfidence(),
                    null,
                    dispute.resolutionNote(),
                    dispute.settledAt(),
                    null,
                    dispute.updatedAt());

            DrsTransferSnapshotResponse fullTransfer = loadTransfer(dispute.txnRef(), transfer);
            DrsRefundSnapshotResponse refund = loadRefund(disputeId);
            return new DrsDisputeDetailResponse(dispute, fullTransfer, refund);
        } catch (EmptyResultDataAccessException ex) {
            throw new DisputeNotFoundException(disputeId);
        }
    }

    public DrsTimelineResponse timeline(Long disputeId) {
        DrsDisputeListItemResponse dispute = detail(disputeId).dispute();
        List<DrsTimelineItemResponse> items = jdbc.query(
                """
                SELECT id, event_type, reference_type, reference_id, actor, payload, trace_id, created_at
                  FROM audit_logs
                 WHERE reference_id = ?
                    OR (reference_type = 'DISPUTE' AND payload LIKE ?)
                 ORDER BY created_at ASC, id ASC
                """,
                (rs, rowNum) -> new DrsTimelineItemResponse(
                        rs.getLong("id"),
                        rs.getString("event_type"),
                        rs.getString("reference_type"),
                        rs.getString("reference_id"),
                        rs.getString("actor"),
                        rs.getString("payload"),
                        rs.getString("trace_id"),
                        time(rs, "created_at")),
                dispute.txnRef(),
                "%\"disputeId\":" + disputeId + "%");
        return new DrsTimelineResponse(disputeId, dispute.txnRef(), items.size(), items);
    }

    public DrsEvidenceReportResponse evidenceReport(Long disputeId) {
        DrsDisputeDetailResponse detail = detail(disputeId);
        DrsTimelineResponse timeline = timeline(disputeId);
        return new DrsEvidenceReportResponse(
                LocalDateTime.now(),
                detail.dispute(),
                detail.transfer(),
                detail.refund(),
                timeline.items());
    }

    public String evidenceReportCsv(Long disputeId) {
        DrsEvidenceReportResponse report = evidenceReport(disputeId);
        StringBuilder csv = new StringBuilder();
        csv.append("section,key,value\n");
        appendCsv(csv, "report", "generatedAt", string(report.generatedAt()));
        appendCsv(csv, "dispute", "disputeId", string(report.dispute().disputeId()));
        appendCsv(csv, "dispute", "txnRef", report.dispute().txnRef());
        appendCsv(csv, "dispute", "status", report.dispute().status());
        appendCsv(csv, "dispute", "disputeType", report.dispute().disputeType());
        appendCsv(csv, "dispute", "raisingPspId", report.dispute().raisingPspId());
        appendCsv(csv, "dispute", "respondingPspId", report.dispute().respondingPspId());
        appendCsv(csv, "dispute", "proposedResolution", report.dispute().proposedResolution());
        appendCsv(csv, "dispute", "proposedBy", report.dispute().proposedBy());
        appendCsv(csv, "dispute", "checkedBy", report.dispute().checkedBy());
        appendCsv(csv, "dispute", "resolutionNote", report.dispute().resolutionNote());
        appendCsv(csv, "transfer", "transferRef", report.transfer().transferRef());
        appendCsv(csv, "transfer", "sourceBank", report.transfer().sourceBank());
        appendCsv(csv, "transfer", "destinationBank", report.transfer().destinationBank());
        appendCsv(csv, "transfer", "amount", string(report.transfer().amount()));
        appendCsv(csv, "transfer", "currency", report.transfer().currency());
        appendCsv(csv, "transfer", "status", report.transfer().status());
        appendCsv(csv, "transfer", "confirmationStatus", report.transfer().confirmationStatus());
        appendCsv(csv, "transfer", "settlementConfidence", report.transfer().settlementConfidence());
        appendCsv(csv, "transfer", "settledAt", string(report.transfer().settledAt()));
        if (report.refund() != null) {
            appendCsv(csv, "refund", "refundId", string(report.refund().refundId()));
            appendCsv(csv, "refund", "refundRef", report.refund().refundRef());
            appendCsv(csv, "refund", "amount", string(report.refund().amount()));
            appendCsv(csv, "refund", "status", report.refund().status());
            appendCsv(csv, "refund", "initiatedAt", string(report.refund().initiatedAt()));
            appendCsv(csv, "refund", "completedAt", string(report.refund().completedAt()));
        }
        int index = 1;
        for (DrsTimelineItemResponse event : report.timeline()) {
            appendCsv(csv, "timeline." + index, "eventType", event.eventType());
            appendCsv(csv, "timeline." + index, "actor", event.actor());
            appendCsv(csv, "timeline." + index, "createdAt", string(event.createdAt()));
            appendCsv(csv, "timeline." + index, "payload", event.payload());
            index++;
        }
        return csv.toString();
    }

    public DrsDashboardSummaryResponse dashboardSummary(Integer requestedLimit) {
        int limit = safeLimit(requestedLimit);
        long open = countStatus("OPEN");
        long pending = countStatus("PENDING_APPROVAL");
        long escalated = countStatus("ESCALATED");
        long resolvedToday = jdbc.queryForObject(
                """
                SELECT COUNT(*)
                  FROM disputes
                 WHERE status IN ('RESOLVED_REFUND','RESOLVED_NO_ACTION','CLOSED')
                   AND resolved_at >= CURRENT_DATE
                   AND resolved_at < CURRENT_DATE + INTERVAL '1 day'
                """,
                Long.class);
        long breached = jdbc.queryForObject(
                """
                SELECT COUNT(*)
                  FROM disputes
                 WHERE status IN ('OPEN','UNDER_REVIEW','PENDING_APPROVAL','ESCALATED')
                   AND sla_deadline < NOW()
                """,
                Long.class);
        long dueSoon = jdbc.queryForObject(
                """
                SELECT COUNT(*)
                  FROM disputes
                 WHERE status IN ('OPEN','UNDER_REVIEW','PENDING_APPROVAL','ESCALATED')
                   AND sla_deadline >= NOW()
                   AND sla_deadline < NOW() + INTERVAL '4 hours'
                """,
                Long.class);
        BigDecimal totalAmount = jdbc.queryForObject(
                """
                SELECT COALESCE(SUM(t.amount), 0)
                  FROM disputes d
                  LEFT JOIN transactions t ON t.transaction_ref = d.txn_ref
                 WHERE d.status IN ('OPEN','UNDER_REVIEW','PENDING_APPROVAL','ESCALATED')
                """,
                BigDecimal.class);
        long refundCompletedToday = jdbc.queryForObject(
                """
                SELECT COUNT(*)
                  FROM refund_transactions
                 WHERE status = 'COMPLETED'
                   AND completed_at >= CURRENT_DATE
                   AND completed_at < CURRENT_DATE + INTERVAL '1 day'
                """,
                Long.class);
        BigDecimal refundCompletedTodayAmount = jdbc.queryForObject(
                """
                SELECT COALESCE(SUM(amount), 0)
                  FROM refund_transactions
                 WHERE status = 'COMPLETED'
                   AND completed_at >= CURRENT_DATE
                   AND completed_at < CURRENT_DATE + INTERVAL '1 day'
                """,
                BigDecimal.class);
        long refundPending = jdbc.queryForObject(
                """
                SELECT COUNT(*)
                  FROM refund_transactions
                 WHERE status = 'INITIATED'
                """,
                Long.class);
        List<DrsStatusCountResponse> statusCounts = jdbc.query(
                """
                SELECT status, COUNT(*) AS count
                  FROM disputes
                 GROUP BY status
                 ORDER BY status
                """,
                (rs, rowNum) -> new DrsStatusCountResponse(rs.getString("status"), rs.getLong("count")));
        List<DrsDisputeListItemResponse> recent = list(null, null, null, null, null, limit).items();
        return new DrsDashboardSummaryResponse(
                LocalDateTime.now(),
                open,
                pending,
                resolvedToday,
                escalated,
                breached,
                dueSoon,
                totalAmount == null ? BigDecimal.ZERO : totalAmount,
                refundCompletedToday,
                refundCompletedTodayAmount == null ? BigDecimal.ZERO : refundCompletedTodayAmount,
                refundPending,
                statusCounts,
                recent);
    }

    private String baseDisputeSelect() {
        return """
                SELECT d.dispute_id,
                       d.txn_ref,
                       d.raising_psp_id,
                       d.responding_psp_id,
                       d.dispute_type,
                       d.status,
                       d.raised_at,
                       d.sla_deadline,
                       d.resolved_at,
                       d.proposed_resolution,
                       d.proposed_by,
                       d.proposed_at,
                       d.checked_by,
                       d.checked_at,
                       d.resolution_note,
                       t.amount,
                       t.currency,
                       t.source_bank,
                       t.destination_bank,
                       t.status AS transfer_status,
                       t.confirmation_status,
                       t.settlement_confidence,
                       t.settled_at,
                       r.refund_id,
                       r.refund_txn_ref,
                       r.amount AS refund_amount,
                       r.status AS refund_status,
                       r.initiated_at AS refund_initiated_at,
                       r.completed_at AS refund_completed_at,
                       COALESCE(d.updated_at, t.updated_at, d.raised_at) AS updated_at
                  FROM disputes d
                  LEFT JOIN transactions t ON t.transaction_ref = d.txn_ref
                  LEFT JOIN LATERAL (
                       SELECT refund_id, refund_txn_ref, amount, status, initiated_at, completed_at
                         FROM refund_transactions
                        WHERE dispute_id = d.dispute_id
                        ORDER BY refund_id DESC
                        LIMIT 1
                  ) r ON true
                """;
    }

    private DrsDisputeListItemResponse mapListItem(ResultSet rs, int rowNum) throws SQLException {
        return new DrsDisputeListItemResponse(
                rs.getLong("dispute_id"),
                rs.getString("txn_ref"),
                rs.getString("raising_psp_id"),
                rs.getString("responding_psp_id"),
                rs.getString("dispute_type"),
                rs.getString("status"),
                time(rs, "raised_at"),
                time(rs, "sla_deadline"),
                time(rs, "resolved_at"),
                rs.getString("proposed_resolution"),
                rs.getString("proposed_by"),
                time(rs, "proposed_at"),
                rs.getString("checked_by"),
                time(rs, "checked_at"),
                rs.getString("resolution_note"),
                rs.getBigDecimal("amount"),
                rs.getString("currency"),
                rs.getString("source_bank"),
                rs.getString("destination_bank"),
                rs.getString("transfer_status"),
                rs.getString("confirmation_status"),
                rs.getString("settlement_confidence"),
                time(rs, "settled_at"),
                time(rs, "updated_at"),
                nullableLong(rs, "refund_id"),
                rs.getString("refund_txn_ref"),
                rs.getBigDecimal("refund_amount"),
                rs.getString("refund_status"),
                time(rs, "refund_initiated_at"),
                time(rs, "refund_completed_at"));
    }

    private DrsTransferSnapshotResponse loadTransfer(String txnRef, DrsTransferSnapshotResponse fallback) {
        try {
            return jdbc.queryForObject(
                    """
                    SELECT transaction_ref, source_bank, destination_bank, amount, currency, status,
                           confirmation_status, settlement_confidence, external_reference, reference,
                           settled_at, created_at, updated_at
                      FROM transactions
                     WHERE transaction_ref = ?
                     LIMIT 1
                    """,
                    (rs, rowNum) -> new DrsTransferSnapshotResponse(
                            rs.getString("transaction_ref"),
                            rs.getString("source_bank"),
                            rs.getString("destination_bank"),
                            rs.getBigDecimal("amount"),
                            rs.getString("currency"),
                            rs.getString("status"),
                            rs.getString("status"),
                            rs.getString("confirmation_status"),
                            rs.getString("settlement_confidence"),
                            rs.getString("external_reference"),
                            rs.getString("reference"),
                            time(rs, "settled_at"),
                            time(rs, "created_at"),
                            time(rs, "updated_at")),
                    txnRef);
        } catch (EmptyResultDataAccessException ex) {
            return fallback;
        }
    }

    private DrsRefundSnapshotResponse loadRefund(Long disputeId) {
        try {
            return jdbc.queryForObject(
                    """
                    SELECT refund_id, dispute_id, original_txn_ref, refund_txn_ref,
                           amount, status, initiated_at, completed_at
                      FROM refund_transactions
                     WHERE dispute_id = ?
                     ORDER BY refund_id DESC
                     LIMIT 1
                    """,
                    (rs, rowNum) -> new DrsRefundSnapshotResponse(
                            rs.getLong("refund_id"),
                            rs.getLong("dispute_id"),
                            rs.getString("original_txn_ref"),
                            rs.getString("refund_txn_ref"),
                            rs.getBigDecimal("amount"),
                            rs.getString("status"),
                            time(rs, "initiated_at"),
                            time(rs, "completed_at")),
                    disputeId);
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
    }

    private long countStatus(String status) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM disputes WHERE status = ?", Long.class, status);
        return count == null ? 0L : count;
    }

    private void appendFilter(StringBuilder where, List<Object> args, String column, String value) {
        if (StringUtils.hasText(value)) {
            where.append(" AND ").append(column).append(" = ?");
            args.add(value);
        }
    }

    private int safeLimit(Integer requestedLimit) {
        if (requestedLimit == null) {
            return DEFAULT_LIMIT;
        }
        return Math.min(Math.max(requestedLimit, 1), MAX_LIMIT);
    }

    private String emptyToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }

    private String string(Object value) {
        return value == null ? "" : value.toString();
    }

    private void appendCsv(StringBuilder csv, String section, String key, String value) {
        csv.append(csv(section)).append(',')
                .append(csv(key)).append(',')
                .append(csv(value)).append('\n');
    }

    private String csv(String value) {
        String safe = value == null ? "" : value;
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }

    private LocalDateTime time(ResultSet rs, String column) throws SQLException {
        java.sql.Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }
}
