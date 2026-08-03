package com.example.switching.fees;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/fees")
@PreAuthorize("hasAnyRole('SYSTEM_ADMIN','OPS_ADMIN','AUDITOR','READ_ONLY')")
public class FeePortalController {
    private final JdbcTemplate jdbc;
    public FeePortalController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping("/decisions")
    public Map<String, Object> decisions(@RequestParam(required = false) String participant,
                                         @RequestParam(required = false) String messageType,
                                         @RequestParam(required = false) Instant since,
                                         @RequestParam(defaultValue = "100") int limit,
                                         @RequestParam(defaultValue = "0") int offset) {
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE fa.assessed_at >= ?");
        params.add(Timestamp.from(since == null ? Instant.now().minusSeconds(7L * 24 * 60 * 60) : since));
        if (participant != null && !participant.isBlank()) { where.append(" AND tp.participant_code=?"); params.add(participant.trim().toUpperCase()); }
        if (messageType != null && !messageType.isBlank()) { where.append(" AND tr.message_type=?"); params.add(messageType.trim().toUpperCase()); }
        String joins = " FROM fee_assessment fa JOIN tariff_rule tr ON tr.id=fa.tariff_rule_id JOIN tariff_version tv ON tv.id=fa.tariff_version_id JOIN tariff_plan tp ON tp.id=tv.plan_id";
        Long total = jdbc.queryForObject("SELECT COUNT(*)" + joins + where, Long.class, params.toArray());
        List<Object> listParams = new ArrayList<>(params); listParams.add(Math.min(Math.max(limit, 1), 500)); listParams.add(Math.max(offset, 0));
        List<Map<String, Object>> items = jdbc.query("""
                SELECT fa.id,fa.transaction_reference,fa.assessed_fee,fa.currency,fa.assessed_at,
                       fa.tariff_rule_id,tr.message_type,tp.participant_code
                """ + joins + where + " ORDER BY fa.assessed_at DESC LIMIT ? OFFSET ?", (rs, row) -> decisionRow(
                    rs.getObject("id").toString(), rs.getString("transaction_reference"), rs.getString("participant_code"),
                    rs.getString("message_type"), rs.getBigDecimal("assessed_fee"), rs.getString("currency"),
                    rs.getObject("tariff_rule_id").toString(), rs.getTimestamp("assessed_at").toInstant().toString()), listParams.toArray());
        return Map.of("items", items, "totalItems", total == null ? 0 : total, "returnedItems", items.size());
    }

    @GetMapping("/exceptions")
    public Map<String, Object> exceptions(@RequestParam(required = false) String status,
                                          @RequestParam(defaultValue = "100") int limit,
                                          @RequestParam(defaultValue = "0") int offset) {
        List<Object> params = new ArrayList<>();
        String where = "";
        if (status != null && !status.isBlank()) { where = " WHERE fe.status=?"; params.add(status.trim().toUpperCase()); }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM fee_exception fe" + where, Long.class, params.toArray());
        List<Object> listParams = new ArrayList<>(params); listParams.add(Math.min(Math.max(limit, 1), 500)); listParams.add(Math.max(offset, 0));
        List<Map<String, Object>> items = jdbc.query("""
                SELECT fe.id,fe.tariff_rule_id,fe.participant_code,fe.message_type,fe.override_type,
                       fe.override_value,fe.reason,fe.requested_by,fe.requested_at,fe.status,fe.valid_from,fe.valid_until
                  FROM fee_exception fe
                """ + where + " ORDER BY fe.requested_at DESC LIMIT ? OFFSET ?", (rs, row) -> exceptionRow(
                    rs.getObject("id").toString(), rs.getObject("tariff_rule_id").toString(), rs.getString("participant_code"),
                    rs.getString("message_type"), rs.getString("override_type"), rs.getBigDecimal("override_value"),
                    rs.getString("reason"), rs.getString("requested_by"), rs.getTimestamp("requested_at").toInstant().toString(),
                    rs.getString("status"), rs.getTimestamp("valid_from") == null ? "" : rs.getTimestamp("valid_from").toInstant().toString(),
                    rs.getTimestamp("valid_until") == null ? "" : rs.getTimestamp("valid_until").toInstant().toString()), listParams.toArray());
        return Map.of("items", items, "totalItems", total == null ? 0 : total, "returnedItems", items.size());
    }

    private static Map<String, Object> decisionRow(String id, String reference, String participant, String messageType,
                                                    Object fee, String currency, String ruleId, String assessedAt) {
        Map<String, Object> item = new java.util.LinkedHashMap<>();
        item.put("decisionId", id); item.put("transactionRef", reference); item.put("participant", participant == null ? "" : participant);
        item.put("messageType", messageType); item.put("grossFee", fee); item.put("promotionDiscount", 0);
        item.put("netFee", fee); item.put("discountRecorded", false); item.put("currency", currency); item.put("ruleId", ruleId); item.put("assessedAt", assessedAt);
        return item;
    }

    private static Map<String, Object> exceptionRow(String id, String ruleId, String participant, String messageType,
                                                     String overrideType, Object overrideValue, String reason, String requestedBy,
                                                     String requestedAt, String status, String validFrom, String validUntil) {
        Map<String, Object> item = new java.util.LinkedHashMap<>();
        item.put("exceptionId", id); item.put("ruleId", ruleId); item.put("participant", participant == null ? "" : participant);
        item.put("messageType", messageType == null ? "" : messageType); item.put("overrideType", overrideType);
        item.put("overrideValue", overrideValue == null ? 0 : overrideValue); item.put("reason", reason); item.put("requestedBy", requestedBy);
        item.put("requestedAt", requestedAt); item.put("status", status); item.put("validFrom", validFrom); item.put("validUntil", validUntil);
        return item;
    }
}
