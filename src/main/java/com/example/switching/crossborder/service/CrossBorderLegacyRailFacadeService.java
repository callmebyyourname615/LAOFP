package com.example.switching.crossborder.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.switching.audit.service.AuditLogService;

@Service
public class CrossBorderLegacyRailFacadeService {

    private static final String ENTITY = "CROSS_BORDER_LEGACY_RAIL";

    private final JdbcTemplate jdbc;
    private final AuditLogService auditLogService;

    public CrossBorderLegacyRailFacadeService(JdbcTemplate jdbc, AuditLogService auditLogService) {
        this.jdbc = jdbc;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public LegacyRailFile exportFile(String rail, String variant, LocalDate statementDate, String actor) {
        LegacyRailRequest request = normalize(rail, variant, statementDate);
        List<RailMessageRow> messages = loadMessages(request.sourceRail(), request.statementDate());
        List<RailReconciliationRow> reconciliations = loadReconciliations(request.sourceRail(), request.statementDate());
        String content = "REPORT".equals(request.kind())
                ? reportCsv(request, messages, reconciliations)
                : settlementFile(request, messages, reconciliations);
        auditLogService.log("CROSS_BORDER_LEGACY_RAIL_FILE_EXPORTED", ENTITY,
                request.legacyRail() + ":" + request.variant() + ":" + request.statementDate(),
                actorOrSource(actor),
                Map.of(
                        "legacyRail", request.legacyRail(),
                        "sourceRail", request.sourceRail(),
                        "variant", request.variant(),
                        "statementDate", request.statementDate(),
                        "messageCount", messages.size(),
                        "reconciliationCount", reconciliations.size(),
                        "sha256", sha256(content)));
        return new LegacyRailFile(filename(request), content);
    }

    private List<RailMessageRow> loadMessages(String sourceRail, LocalDate statementDate) {
        return jdbc.query("""
                SELECT rail, direction, external_ref, internal_ref, message_type,
                       status, acknowledgement_code, settlement_date, received_at,
                       sent_at, completed_at
                  FROM cross_border_rail_message
                 WHERE rail = ?
                   AND COALESCE(settlement_date, DATE(created_at)) = ?
                 ORDER BY external_ref ASC
                """, (rs, rowNum) -> new RailMessageRow(
                rs.getString("rail"),
                rs.getString("direction"),
                rs.getString("external_ref"),
                rs.getString("internal_ref"),
                rs.getString("message_type"),
                rs.getString("status"),
                rs.getString("acknowledgement_code"),
                rs.getObject("settlement_date", LocalDate.class),
                rs.getObject("received_at", LocalDateTime.class),
                rs.getObject("sent_at", LocalDateTime.class),
                rs.getObject("completed_at", LocalDateTime.class)),
                sourceRail, statementDate);
    }

    private List<RailReconciliationRow> loadReconciliations(String sourceRail, LocalDate statementDate) {
        return jdbc.query("""
                SELECT rail, statement_date, external_ref, internal_ref,
                       external_amount, internal_amount, currency, status,
                       discrepancy_reason, evidence_sha256
                  FROM cross_border_rail_reconciliation
                 WHERE rail = ? AND statement_date = ?
                 ORDER BY external_ref ASC
                """, (rs, rowNum) -> new RailReconciliationRow(
                rs.getString("rail"),
                rs.getObject("statement_date", LocalDate.class),
                rs.getString("external_ref"),
                rs.getString("internal_ref"),
                rs.getBigDecimal("external_amount"),
                rs.getBigDecimal("internal_amount"),
                rs.getString("currency"),
                rs.getString("status"),
                rs.getString("discrepancy_reason"),
                rs.getString("evidence_sha256")),
                sourceRail, statementDate);
    }

    private String settlementFile(LegacyRailRequest request, List<RailMessageRow> messages,
                                  List<RailReconciliationRow> reconciliations) {
        BigDecimal externalTotal = reconciliations.stream()
                .map(RailReconciliationRow::externalAmount)
                .filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal internalTotal = reconciliations.stream()
                .map(RailReconciliationRow::internalAmount)
                .filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        StringBuilder body = new StringBuilder();
        appendPipe(body, "HDR", request.legacyRail(), request.sourceRail(), request.variant(),
                request.statementDate().toString(), String.valueOf(messages.size()),
                String.valueOf(reconciliations.size()), money(externalTotal), money(internalTotal));
        for (RailMessageRow message : messages) {
            appendPipe(body, "MSG", message.direction(), message.externalRef(), message.internalRef(),
                    message.messageType(), message.status(), safe(message.acknowledgementCode()),
                    safe(message.settlementDate()), safe(message.completedAt()));
        }
        for (RailReconciliationRow reconciliation : reconciliations) {
            appendPipe(body, "REC", reconciliation.externalRef(), safe(reconciliation.internalRef()),
                    money(reconciliation.externalAmount()), money(reconciliation.internalAmount()),
                    reconciliation.currency(), reconciliation.status(), safe(reconciliation.discrepancyReason()),
                    reconciliation.evidenceSha256());
        }
        appendPipe(body, "TRL", String.valueOf(messages.size()), String.valueOf(reconciliations.size()),
                money(externalTotal), money(internalTotal));
        appendPipe(body, "CHK", sha256(body.toString()));
        return body.toString();
    }

    private String reportCsv(LegacyRailRequest request, List<RailMessageRow> messages,
                             List<RailReconciliationRow> reconciliations) {
        StringBuilder csv = new StringBuilder();
        csv.append("section,legacyRail,sourceRail,variant,statementDate,count\n");
        csv.append(csvRow("SUMMARY", request.legacyRail(), request.sourceRail(), request.variant(),
                request.statementDate().toString(), String.valueOf(messages.size() + reconciliations.size())));
        csv.append("\nmessages\n");
        csv.append("direction,externalRef,internalRef,messageType,status,acknowledgementCode,settlementDate,completedAt\n");
        for (RailMessageRow message : messages) {
            csv.append(csvRow(message.direction(), message.externalRef(), message.internalRef(),
                    message.messageType(), message.status(), message.acknowledgementCode(),
                    safe(message.settlementDate()), safe(message.completedAt())));
        }
        csv.append("\nreconciliation\n");
        csv.append("externalRef,internalRef,externalAmount,internalAmount,currency,status,discrepancyReason,evidenceSha256\n");
        for (RailReconciliationRow reconciliation : reconciliations) {
            csv.append(csvRow(reconciliation.externalRef(), reconciliation.internalRef(),
                    money(reconciliation.externalAmount()), money(reconciliation.internalAmount()),
                    reconciliation.currency(), reconciliation.status(), reconciliation.discrepancyReason(),
                    reconciliation.evidenceSha256()));
        }
        return csv.toString();
    }

    private LegacyRailRequest normalize(String rail, String variant, LocalDate statementDate) {
        String legacyRail = required(rail, "rail").toUpperCase();
        String safeVariant = required(variant, "variant").toUpperCase();
        LocalDate date = statementDate == null ? LocalDate.now() : statementDate;
        String kind = safeVariant.contains("REPORT") || safeVariant.contains("SUMMARY") ? "REPORT" : "FILE";
        String sourceRail = switch (legacyRail) {
            case "ITMX", "THAISCAN", "THAISCAN_LAO" -> "PROMPTPAY";
            case "NAPAS", "UPI", "BAKONG", "PROMPTPAY" -> legacyRail;
            case "ECNY", "LFX" -> legacyRail;
            default -> throw new IllegalArgumentException("Unsupported legacy cross-border rail: " + rail);
        };
        return new LegacyRailRequest(legacyRail, sourceRail, safeVariant, kind, date);
    }

    private String filename(LegacyRailRequest request) {
        return "crossborder-" + request.legacyRail().toLowerCase()
                + "-" + request.variant().toLowerCase().replace('_', '-')
                + "-" + request.statementDate() + ("REPORT".equals(request.kind()) ? ".csv" : ".txt");
    }

    private void appendPipe(StringBuilder builder, String... values) {
        StringJoiner joiner = new StringJoiner("|");
        for (String value : values) {
            joiner.add(safe(value).replace("|", "/").replace("\n", " ").replace("\r", " "));
        }
        builder.append(joiner).append('\n');
    }

    private String csvRow(String... values) {
        StringJoiner joiner = new StringJoiner(",");
        for (String value : values) {
            joiner.add("\"" + safe(value).replace("\"", "\"\"") + "\"");
        }
        return joiner + "\n";
    }

    private String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }

    private String money(BigDecimal value) {
        return value == null ? "" : value.toPlainString();
    }

    private String safe(Object value) {
        return value == null ? "" : value.toString();
    }

    private String actorOrSource(String actor) {
        return actor != null && !actor.isBlank() ? actor : "CROSS_BORDER";
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private record LegacyRailRequest(String legacyRail, String sourceRail, String variant, String kind, LocalDate statementDate) {}

    private record RailMessageRow(String rail, String direction, String externalRef, String internalRef,
                                  String messageType, String status, String acknowledgementCode,
                                  LocalDate settlementDate, LocalDateTime receivedAt,
                                  LocalDateTime sentAt, LocalDateTime completedAt) {}

    private record RailReconciliationRow(String rail, LocalDate statementDate, String externalRef, String internalRef,
                                         BigDecimal externalAmount, BigDecimal internalAmount, String currency,
                                         String status, String discrepancyReason, String evidenceSha256) {}

    public record LegacyRailFile(String filename, String content) {}
}
