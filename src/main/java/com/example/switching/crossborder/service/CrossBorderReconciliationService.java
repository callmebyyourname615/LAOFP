package com.example.switching.crossborder.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CrossBorderReconciliationService {

    private static final Set<String> RAILS =
            Set.of("PROMPTPAY", "BAKONG", "NAPAS", "UPI");

    private final JdbcTemplate jdbc;

    public CrossBorderReconciliationService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public Map<String, Integer> reconcile(
            String rail,
            LocalDate statementDate,
            List<StatementItem> items) {
        String normalizedRail = normalizeRail(rail);
        if (statementDate == null || items == null) {
            throw new IllegalArgumentException("Rail statement date and items are required");
        }
        Set<String> statementReferences = new HashSet<>();
        int matched = 0;
        int mismatched = 0;
        int missingInternal = 0;
        int missingExternal = 0;

        for (StatementItem item : List.copyOf(items)) {
            validate(item);
            if (!statementReferences.add(item.externalRef())) {
                throw new IllegalArgumentException(
                        "Duplicate external reference in rail statement");
            }
            InternalEntry internal = findInternal(normalizedRail, item.externalRef());
            Result result;
            if (internal == null) {
                result = new Result(
                        "MISSING_INTERNAL",
                        "No outbound rail message matches the statement item",
                        null,
                        null,
                        null);
                missingInternal++;
            } else if (internal.amount() == null) {
                result = new Result(
                        "MISSING_INTERNAL",
                        "Internal payment amount is unavailable",
                        internal.internalRef(),
                        null,
                        internal.currency());
                missingInternal++;
            } else if (internal.amount().compareTo(item.amount()) != 0) {
                result = new Result(
                        "AMOUNT_MISMATCH",
                        "External and internal amounts differ",
                        internal.internalRef(),
                        internal.amount(),
                        internal.currency());
                mismatched++;
            } else if (!item.currency().equalsIgnoreCase(internal.currency())) {
                result = new Result(
                        "CURRENCY_MISMATCH",
                        "External and internal currencies differ",
                        internal.internalRef(),
                        internal.amount(),
                        internal.currency());
                mismatched++;
            } else {
                result = new Result(
                        "MATCHED",
                        null,
                        internal.internalRef(),
                        internal.amount(),
                        internal.currency());
                matched++;
            }
            persist(
                    normalizedRail,
                    statementDate,
                    item.externalRef(),
                    item.amount(),
                    item.currency(),
                    result);
        }

        for (InternalEntry internal : findExpectedInternal(normalizedRail, statementDate)) {
            if (!statementReferences.contains(internal.externalRef())) {
                Result result = new Result(
                        "MISSING_EXTERNAL",
                        "Outbound rail message is absent from the partner statement",
                        internal.internalRef(),
                        internal.amount(),
                        internal.currency());
                persist(
                        normalizedRail,
                        statementDate,
                        internal.externalRef(),
                        BigDecimal.ZERO,
                        internal.currency(),
                        result);
                missingExternal++;
            }
        }

        Map<String, Integer> summary = new LinkedHashMap<>();
        summary.put("matched", matched);
        summary.put("mismatched", mismatched);
        summary.put("missingInternal", missingInternal);
        summary.put("missingExternal", missingExternal);
        summary.put("total", matched + mismatched + missingInternal + missingExternal);
        return Map.copyOf(summary);
    }

    private InternalEntry findInternal(String rail, String externalReference) {
        List<InternalEntry> rows = jdbc.query("""
                SELECT m.external_ref,
                       m.internal_ref,
                       q.source_amount,
                       q.source_currency
                  FROM cross_border_rail_message m
                  LEFT JOIN crossborder_transfers t
                    ON t.txn_ref=m.internal_ref
                  LEFT JOIN fx_quotes q
                    ON q.quote_id=t.quote_id
                 WHERE m.rail=?
                   AND m.direction='OUTBOUND'
                   AND m.external_ref=?
                """, (resultSet, rowNumber) -> new InternalEntry(
                        resultSet.getString("external_ref"),
                        resultSet.getString("internal_ref"),
                        resultSet.getBigDecimal("source_amount"),
                        resultSet.getString("source_currency")),
                rail,
                externalReference);
        if (rows.size() > 1) {
            throw new IllegalStateException("Duplicate durable rail journal entry detected");
        }
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private List<InternalEntry> findExpectedInternal(
            String rail,
            LocalDate statementDate) {
        return new ArrayList<>(jdbc.query("""
                SELECT m.external_ref,
                       m.internal_ref,
                       q.source_amount,
                       q.source_currency
                  FROM cross_border_rail_message m
                  LEFT JOIN crossborder_transfers t
                    ON t.txn_ref=m.internal_ref
                  LEFT JOIN fx_quotes q
                    ON q.quote_id=t.quote_id
                 WHERE m.rail=?
                   AND m.direction='OUTBOUND'
                   AND COALESCE(m.settlement_date,
                                CAST(m.sent_at AS date),
                                CAST(m.created_at AS date))=?
                   AND m.status IN ('SUBMITTED','ACKNOWLEDGED','COMPLETED')
                """, (resultSet, rowNumber) -> new InternalEntry(
                        resultSet.getString("external_ref"),
                        resultSet.getString("internal_ref"),
                        resultSet.getBigDecimal("source_amount"),
                        resultSet.getString("source_currency")),
                rail,
                statementDate));
    }

    private void persist(
            String rail,
            LocalDate date,
            String externalReference,
            BigDecimal externalAmount,
            String externalCurrency,
            Result result) {
        String evidence = sha256(String.join("|",
                rail,
                date.toString(),
                externalReference,
                externalAmount.toPlainString(),
                externalCurrency,
                result.status(),
                String.valueOf(result.internalRef()),
                String.valueOf(result.internalAmount()),
                String.valueOf(result.internalCurrency())));
        jdbc.update("""
                INSERT INTO cross_border_rail_reconciliation(
                    id, rail, statement_date, external_ref, internal_ref,
                    external_amount, internal_amount, currency, status,
                    discrepancy_reason, evidence_sha256)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(rail, statement_date, external_ref) DO UPDATE SET
                    internal_ref=excluded.internal_ref,
                    external_amount=excluded.external_amount,
                    internal_amount=excluded.internal_amount,
                    currency=excluded.currency,
                    status=excluded.status,
                    discrepancy_reason=excluded.discrepancy_reason,
                    evidence_sha256=excluded.evidence_sha256
                """,
                UUID.randomUUID(),
                rail,
                date,
                externalReference,
                result.internalRef(),
                externalAmount,
                result.internalAmount(),
                externalCurrency,
                result.status(),
                result.reason(),
                evidence);
    }

    private static void validate(StatementItem item) {
        if (item == null
                || item.externalRef() == null
                || item.externalRef().isBlank()
                || item.externalRef().length() > 160
                || item.amount() == null
                || item.amount().signum() < 0
                || item.currency() == null
                || !item.currency().matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("Invalid cross-border statement item");
        }
    }

    private static String normalizeRail(String rail) {
        String normalized = rail == null
                ? ""
                : rail.trim().toUpperCase(Locale.ROOT);
        if (!RAILS.contains(normalized)) {
            throw new IllegalArgumentException("Unsupported cross-border rail");
        }
        return normalized;
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    public record StatementItem(
            String externalRef,
            BigDecimal amount,
            String currency) {}

    private record InternalEntry(
            String externalRef,
            String internalRef,
            BigDecimal amount,
            String currency) {}

    private record Result(
            String status,
            String reason,
            String internalRef,
            BigDecimal internalAmount,
            String internalCurrency) {}
}
