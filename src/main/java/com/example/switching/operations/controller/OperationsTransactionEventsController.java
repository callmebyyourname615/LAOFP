package com.example.switching.operations.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Query endpoints for the {@code transaction_events} and {@code payment_flows} tables.
 *
 * <pre>
 *  GET /api/operations/transaction-events/{transactionRef}        — lifecycle events for a transfer
 *  GET /api/operations/payment-flows/{transactionRef}             — flow record for a transfer
 *  GET /api/operations/transaction-events?date=&type=             — events by date/type
 * </pre>
 */
@RestController
@RequestMapping("/api/operations")
public class OperationsTransactionEventsController {

    private final JdbcTemplate jdbcTemplate;

    public OperationsTransactionEventsController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // ── Transaction events for one transfer ───────────────────────────────────

    @GetMapping("/transaction-events/{transactionRef}")
    public ResponseEntity<List<TransactionEventRow>> getEvents(
            @PathVariable String transactionRef,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        String sql;
        Object[] args;

        if (date != null) {
            // Pruned query that hits a single partition
            sql  = "SELECT id, transaction_ref, event_type, payload::text, actor, business_date, occurred_at "
                 + "FROM transaction_events WHERE transaction_ref = ? AND business_date = ? "
                 + "ORDER BY occurred_at ASC";
            args = new Object[]{transactionRef, date};
        } else {
            // Cross-partition scan (all days) — for small queries this is fine
            sql  = "SELECT id, transaction_ref, event_type, payload::text, actor, business_date, occurred_at "
                 + "FROM transaction_events WHERE transaction_ref = ? "
                 + "ORDER BY occurred_at ASC";
            args = new Object[]{transactionRef};
        }

        List<TransactionEventRow> rows = jdbcTemplate.query(sql, args, (rs, n) -> new TransactionEventRow(
                rs.getLong("id"),
                rs.getString("transaction_ref"),
                rs.getString("event_type"),
                rs.getString("payload"),
                rs.getString("actor"),
                rs.getObject("business_date", LocalDate.class),
                rs.getObject("occurred_at", LocalDateTime.class)));

        return ResponseEntity.ok(rows);
    }

    // ── Payment flow for one transfer ─────────────────────────────────────────

    @GetMapping("/payment-flows/{transactionRef}")
    public ResponseEntity<List<PaymentFlowRow>> getFlow(
            @PathVariable String transactionRef,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        String sql;
        Object[] args;

        if (date != null) {
            sql  = "SELECT id, flow_ref, inquiry_ref, transaction_ref, source_bank, destination_bank, "
                 + "channel_id, amount, currency, status, business_date, initiated_at, settled_at, failed_at "
                 + "FROM payment_flows WHERE transaction_ref = ? AND business_date = ? "
                 + "ORDER BY initiated_at DESC";
            args = new Object[]{transactionRef, date};
        } else {
            sql  = "SELECT id, flow_ref, inquiry_ref, transaction_ref, source_bank, destination_bank, "
                 + "channel_id, amount, currency, status, business_date, initiated_at, settled_at, failed_at "
                 + "FROM payment_flows WHERE transaction_ref = ? "
                 + "ORDER BY initiated_at DESC";
            args = new Object[]{transactionRef};
        }

        List<PaymentFlowRow> rows = jdbcTemplate.query(sql, args, (rs, n) -> new PaymentFlowRow(
                rs.getLong("id"),
                rs.getString("flow_ref"),
                rs.getString("inquiry_ref"),
                rs.getString("transaction_ref"),
                rs.getString("source_bank"),
                rs.getString("destination_bank"),
                rs.getString("channel_id"),
                rs.getBigDecimal("amount"),
                rs.getString("currency"),
                rs.getString("status"),
                rs.getObject("business_date",  LocalDate.class),
                rs.getObject("initiated_at",   LocalDateTime.class),
                rs.getObject("settled_at",     LocalDateTime.class),
                rs.getObject("failed_at",      LocalDateTime.class)));

        return ResponseEntity.ok(rows);
    }

    // ── Events by date/type (ops dashboard, cross-transfer) ──────────────────

    @GetMapping("/transaction-events")
    public ResponseEntity<List<TransactionEventRow>> listEventsByDate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String type) {

        String sql;
        Object[] args;
        if (type != null) {
            sql  = "SELECT id, transaction_ref, event_type, payload::text, actor, business_date, occurred_at "
                 + "FROM transaction_events WHERE business_date = ? AND event_type = ? "
                 + "ORDER BY occurred_at DESC LIMIT 500";
            args = new Object[]{date, type.toUpperCase()};
        } else {
            sql  = "SELECT id, transaction_ref, event_type, payload::text, actor, business_date, occurred_at "
                 + "FROM transaction_events WHERE business_date = ? "
                 + "ORDER BY occurred_at DESC LIMIT 500";
            args = new Object[]{date};
        }

        List<TransactionEventRow> rows = jdbcTemplate.query(sql, args, (rs, n) -> new TransactionEventRow(
                rs.getLong("id"),
                rs.getString("transaction_ref"),
                rs.getString("event_type"),
                rs.getString("payload"),
                rs.getString("actor"),
                rs.getObject("business_date", LocalDate.class),
                rs.getObject("occurred_at",   LocalDateTime.class)));

        return ResponseEntity.ok(rows);
    }

    // ── Inner response records ────────────────────────────────────────────────

    public record TransactionEventRow(
            long id,
            String transactionRef,
            String eventType,
            String payload,
            String actor,
            LocalDate businessDate,
            LocalDateTime occurredAt) {}

    public record PaymentFlowRow(
            long id,
            String flowRef,
            String inquiryRef,
            String transactionRef,
            String sourceBank,
            String destinationBank,
            String channelId,
            java.math.BigDecimal amount,
            String currency,
            String status,
            LocalDate businessDate,
            LocalDateTime initiatedAt,
            LocalDateTime settledAt,
            LocalDateTime failedAt) {}
}
