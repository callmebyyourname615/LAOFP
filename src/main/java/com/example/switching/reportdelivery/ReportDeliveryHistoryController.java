package com.example.switching.reportdelivery;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${switching.api.v1-prefix:/v1}/operator/report-delivery-history")
@ConditionalOnProperty(prefix = "switching.phase-ii.report-delivery", name = "enabled", havingValue = "true")
@PreAuthorize("hasAnyRole('SYSTEM_ADMIN','OPS_ADMIN','AUDITOR')")
public class ReportDeliveryHistoryController {
    private final JdbcTemplate jdbc;
    public ReportDeliveryHistoryController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping
    public Map<String, Object> list(@RequestParam(required = false) String status,
                                    @RequestParam(required = false) Instant since,
                                    @RequestParam(defaultValue = "100") int limit,
                                    @RequestParam(defaultValue = "0") int offset) {
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE r.created_at >= ?");
        params.add(Timestamp.from(since == null ? Instant.now().minusSeconds(30L * 24 * 60 * 60) : since));
        if (status != null && !status.isBlank()) { where.append(" AND r.status=?"); params.add(status.trim().toUpperCase()); }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM report_delivery_run r" + where, Long.class, params.toArray());
        List<Object> listParams = new ArrayList<>(params);
        listParams.add(Math.min(Math.max(limit, 1), 500)); listParams.add(Math.max(offset, 0));
        List<Map<String, Object>> items = jdbc.query("""
                SELECT r.id,r.created_at,r.delivered_at,r.status,r.artifact_id,r.last_error_code,
                       s.report_type,s.recipient_participant_id,s.delivery_channel
                  FROM report_delivery_run r JOIN report_delivery_schedule s ON s.id=r.schedule_id
                """ + where + " ORDER BY r.created_at DESC LIMIT ? OFFSET ?", (rs, row) -> Map.<String, Object>of(
                    "runId", rs.getObject("id").toString(),
                    "reportName", rs.getString("report_type"),
                    "date", rs.getTimestamp("delivered_at") == null ? rs.getTimestamp("created_at").toInstant().toString() : rs.getTimestamp("delivered_at").toInstant().toString(),
                    "status", rs.getString("status"),
                    "format", "",
                    "recipient", rs.getString("recipient_participant_id"),
                    "deliveryChannel", rs.getString("delivery_channel"),
                    "artifactId", rs.getObject("artifact_id") == null ? "" : rs.getObject("artifact_id").toString(),
                    "errorMessage", rs.getString("last_error_code") == null ? "" : rs.getString("last_error_code")), listParams.toArray());
        return Map.of("items", items, "totalItems", total == null ? 0 : total, "returnedItems", items.size());
    }
}
