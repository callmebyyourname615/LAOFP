package com.example.switching.finlink;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.switching.audit.service.AuditLogService;
import com.example.switching.ledger.FinancialControlLedgerService;
import com.example.switching.ledger.LedgerLine;

@Service
public class FinlinkTreasuryService {

    private static final String ENTITY = "FINLINK_TREASURY_EVENT";
    private static final String TRANSIT_CLEARING = "FINLINK_TRANSIT_CLEARING";
    private static final String SETTLEMENT_PAYABLE = "FINLINK_SETTLEMENT_PAYABLE";
    private static final String LIFMIS_RECEIVABLE = "FINLINK_LIFMIS_RECEIVABLE";
    private static final String TAX_PAYABLE = "FINLINK_TAX_PAYABLE";
    private static final String ROADTAX_PAYABLE = "FINLINK_ROADTAX_PAYABLE";

    private final JdbcTemplate jdbc;
    private final FinancialControlLedgerService ledgerService;
    private final AuditLogService auditLogService;

    public FinlinkTreasuryService(JdbcTemplate jdbc,
                                  FinancialControlLedgerService ledgerService,
                                  AuditLogService auditLogService) {
        this.jdbc = jdbc;
        this.ledgerService = ledgerService;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public FinlinkEventResponse recordTransitFundMove(TransitFundMoveRequest request, String actor) {
        require(request != null, "request is required");
        String idempotencyKey = text(request.idempotencyKey(), "idempotencyKey");
        String eventRef = defaultText(request.eventRef(), "FL-TFM-" + shortHash(idempotencyKey));
        LocalDate businessDate = date(request.businessDate());
        String currency = currency(request.currency());
        BigDecimal amount = positive(request.amount());
        String debitAccount = defaultText(request.debitAccountCode(), TRANSIT_CLEARING);
        String creditAccount = defaultText(request.creditAccountCode(), SETTLEMENT_PAYABLE);

        FinlinkEventResponse existing = findByIdempotencyKey(idempotencyKey);
        if (existing != null) {
            return existing;
        }

        UUID journalId = ledgerService.post("FINLINK_TRANSIT_FUND_MOVE", eventRef, businessDate, currency, actorOrSource(actor),
                List.of(
                        new LedgerLine(debitAccount, LedgerLine.Side.DEBIT, amount, request.narrative()),
                        new LedgerLine(creditAccount, LedgerLine.Side.CREDIT, amount, request.narrative())));

        return insertEvent(new EventInsert(
                eventRef, idempotencyKey, "TRANSIT_FUND_MOVE", defaultText(request.productCode(), "TRANSIT"),
                businessDate, currency, amount, debitAccount, creditAccount, request.counterpartyRef(),
                request.externalReference(), "POSTED", journalId, actorOrSource(actor)));
    }

    @Transactional
    public FinlinkEventResponse recordLifmisInform(LifmisInformRequest request, String actor) {
        require(request != null, "request is required");
        String idempotencyKey = text(request.idempotencyKey(), "idempotencyKey");
        FinlinkEventResponse existing = findByIdempotencyKey(idempotencyKey);
        if (existing != null) {
            return existing;
        }

        String eventRef = defaultText(request.eventRef(), "FL-LIFMIS-" + shortHash(idempotencyKey));
        String productCode = defaultText(request.productCode(), "LIFMIS");
        String currency = currency(request.currency());
        BigDecimal amount = positive(request.amount());
        String debitAccount = defaultText(request.debitAccountCode(), LIFMIS_RECEIVABLE);
        String creditAccount = defaultText(request.creditAccountCode(), TAX_PAYABLE);

        FinlinkEventResponse response = insertEvent(new EventInsert(
                eventRef, idempotencyKey, "LIFMIS_INFORM", productCode, date(request.businessDate()),
                currency, amount, debitAccount, creditAccount, request.lifmisReference(),
                request.externalReference(), "INFORMED", null, actorOrSource(actor)));
        jdbc.update("UPDATE finlink_treasury_event SET informed_at = now() WHERE event_ref = ?", eventRef);
        auditLogService.log("FINLINK_LIFMIS_INFORMED", ENTITY, eventRef, actorOrSource(actor),
                Map.of("eventRef", eventRef, "lifmisReference", safe(request.lifmisReference()), "amount", amount));
        return findByIdempotencyKey(idempotencyKey);
    }

    @Transactional(readOnly = true)
    public FinlinkFile exportSettlementFile(String eventType, LocalDate businessDate, String actor) {
        String type = text(eventType, "eventType");
        LocalDate date = date(businessDate);
        List<FinlinkEventResponse> events = jdbc.query("""
                SELECT event_ref, event_type, product_code, business_date, currency, amount,
                       debit_account_code, credit_account_code, counterparty_ref, external_reference,
                       status, journal_id, created_at, posted_at, informed_at
                  FROM finlink_treasury_event
                 WHERE event_type = ? AND business_date = ?
                 ORDER BY event_ref ASC
                """, (rs, rowNum) -> mapEvent(rs.getString("event_ref"), rs.getString("event_type"),
                rs.getString("product_code"), rs.getObject("business_date", LocalDate.class),
                rs.getString("currency"), rs.getBigDecimal("amount"), rs.getString("debit_account_code"),
                rs.getString("credit_account_code"), rs.getString("counterparty_ref"),
                rs.getString("external_reference"), rs.getString("status"),
                rs.getObject("journal_id", UUID.class), rs.getObject("created_at", LocalDateTime.class),
                rs.getObject("posted_at", LocalDateTime.class), rs.getObject("informed_at", LocalDateTime.class)),
                type, date);
        String content = fileContent(type, date, events);
        auditLogService.log("FINLINK_SETTLEMENT_FILE_EXPORTED", ENTITY, type + ":" + date, actorOrSource(actor),
                Map.of("eventType", type, "businessDate", date, "rowCount", events.size(), "sha256", sha256(content)));
        return new FinlinkFile(filename(type, date), content);
    }

    private FinlinkEventResponse insertEvent(EventInsert event) {
        UUID id = UUID.randomUUID();
        String payloadHash = sha256(String.join("|",
                event.eventRef(), event.idempotencyKey(), event.eventType(), event.productCode(),
                event.businessDate().toString(), event.currency(), event.amount().toPlainString(),
                safe(event.counterpartyRef()), safe(event.externalReference())));
        try {
            jdbc.update("""
                    INSERT INTO finlink_treasury_event(
                        id, event_ref, idempotency_key, event_type, product_code, business_date,
                        currency, amount, debit_account_code, credit_account_code, counterparty_ref,
                        external_reference, status, journal_id, payload_hash, requested_by, posted_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CASE WHEN ? = 'POSTED' THEN now() ELSE NULL END)
                    """,
                    id, event.eventRef(), event.idempotencyKey(), event.eventType(), event.productCode(),
                    event.businessDate(), event.currency(), event.amount(), event.debitAccountCode(),
                    event.creditAccountCode(), event.counterpartyRef(), event.externalReference(),
                    event.status(), event.journalId(), payloadHash, event.actor(), event.status());
        } catch (DuplicateKeyException duplicate) {
            return findByIdempotencyKey(event.idempotencyKey());
        }
        auditLogService.log("FINLINK_TREASURY_EVENT_RECORDED", ENTITY, event.eventRef(), event.actor(),
                Map.of("eventRef", event.eventRef(), "eventType", event.eventType(), "status", event.status(),
                        "payloadHash", payloadHash));
        return findByIdempotencyKey(event.idempotencyKey());
    }

    private FinlinkEventResponse findByIdempotencyKey(String idempotencyKey) {
        List<FinlinkEventResponse> rows = jdbc.query("""
                SELECT event_ref, event_type, product_code, business_date, currency, amount,
                       debit_account_code, credit_account_code, counterparty_ref, external_reference,
                       status, journal_id, created_at, posted_at, informed_at
                  FROM finlink_treasury_event
                 WHERE idempotency_key = ?
                """, (rs, rowNum) -> mapEvent(rs.getString("event_ref"), rs.getString("event_type"),
                rs.getString("product_code"), rs.getObject("business_date", LocalDate.class),
                rs.getString("currency"), rs.getBigDecimal("amount"), rs.getString("debit_account_code"),
                rs.getString("credit_account_code"), rs.getString("counterparty_ref"),
                rs.getString("external_reference"), rs.getString("status"),
                rs.getObject("journal_id", UUID.class), rs.getObject("created_at", LocalDateTime.class),
                rs.getObject("posted_at", LocalDateTime.class), rs.getObject("informed_at", LocalDateTime.class)),
                idempotencyKey);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private FinlinkEventResponse mapEvent(String eventRef, String eventType, String productCode, LocalDate businessDate,
                                          String currency, BigDecimal amount, String debitAccountCode,
                                          String creditAccountCode, String counterpartyRef, String externalReference,
                                          String status, UUID journalId, LocalDateTime createdAt,
                                          LocalDateTime postedAt, LocalDateTime informedAt) {
        return new FinlinkEventResponse(eventRef, eventType, productCode, businessDate, currency, amount,
                debitAccountCode, creditAccountCode, counterpartyRef, externalReference, status, journalId,
                createdAt, postedAt, informedAt);
    }

    private String fileContent(String eventType, LocalDate businessDate, List<FinlinkEventResponse> events) {
        StringBuilder body = new StringBuilder();
        BigDecimal total = events.stream().map(FinlinkEventResponse::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        append(body, "HDR", eventType, businessDate.toString(), String.valueOf(events.size()), total.toPlainString());
        for (FinlinkEventResponse event : events) {
            append(body, "DTL", event.eventRef(), event.productCode(), event.currency(), event.amount().toPlainString(),
                    safe(event.debitAccountCode()), safe(event.creditAccountCode()), safe(event.counterpartyRef()),
                    safe(event.externalReference()), event.status(), safe(event.journalId()));
        }
        append(body, "TRL", String.valueOf(events.size()), total.toPlainString());
        append(body, "CHK", sha256(body.toString()));
        return body.toString();
    }

    private String filename(String eventType, LocalDate businessDate) {
        return "finlink-" + eventType.toLowerCase().replace('_', '-') + "-" + businessDate + ".txt";
    }

    private void append(StringBuilder builder, String... values) {
        StringJoiner joiner = new StringJoiner("|");
        for (String value : values) {
            joiner.add(safe(value).replace("|", "/").replace("\n", " ").replace("\r", " "));
        }
        builder.append(joiner).append('\n');
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private String shortHash(String value) {
        return sha256(value).substring(0, 16).toUpperCase();
    }

    private String actorOrSource(String actor) {
        return actor != null && !actor.isBlank() ? actor : "FINLINK";
    }

    private String text(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }

    private String defaultText(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private LocalDate date(LocalDate value) {
        if (value == null) {
            throw new IllegalArgumentException("businessDate is required");
        }
        return value;
    }

    private String currency(String value) {
        String currency = text(value, "currency");
        if (!currency.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("currency must be ISO-4217 uppercase");
        }
        return currency;
    }

    private BigDecimal positive(BigDecimal value) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        return value.stripTrailingZeros();
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    private String safe(Object value) {
        return value == null ? "" : value.toString();
    }

    public record TransitFundMoveRequest(
            String idempotencyKey,
            String eventRef,
            LocalDate businessDate,
            String productCode,
            String currency,
            BigDecimal amount,
            String debitAccountCode,
            String creditAccountCode,
            String counterpartyRef,
            String externalReference,
            String narrative) {}

    public record LifmisInformRequest(
            String idempotencyKey,
            String eventRef,
            LocalDate businessDate,
            String productCode,
            String currency,
            BigDecimal amount,
            String debitAccountCode,
            String creditAccountCode,
            String lifmisReference,
            String externalReference) {}

    private record EventInsert(
            String eventRef,
            String idempotencyKey,
            String eventType,
            String productCode,
            LocalDate businessDate,
            String currency,
            BigDecimal amount,
            String debitAccountCode,
            String creditAccountCode,
            String counterpartyRef,
            String externalReference,
            String status,
            UUID journalId,
            String actor) {}

    public record FinlinkEventResponse(
            String eventRef,
            String eventType,
            String productCode,
            LocalDate businessDate,
            String currency,
            BigDecimal amount,
            String debitAccountCode,
            String creditAccountCode,
            String counterpartyRef,
            String externalReference,
            String status,
            UUID journalId,
            LocalDateTime createdAt,
            LocalDateTime postedAt,
            LocalDateTime informedAt) {}

    public record FinlinkFile(String filename, String content) {}
}
