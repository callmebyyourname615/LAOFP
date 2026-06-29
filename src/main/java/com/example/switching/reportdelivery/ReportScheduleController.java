package com.example.switching.reportdelivery;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.type.TypeReference;
import com.example.switching.common.PhaseIIAuditPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("${switching.api.v1-prefix:/v1}/operator/report-delivery-schedules")
@ConditionalOnProperty(
        prefix = "switching.phase-ii.report-delivery",
        name = "enabled",
        havingValue = "true")
public class ReportScheduleController {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final ReportDestinationResolver destinations;
    private final PhaseIIAuditPublisher audit;

    public ReportScheduleController(
            JdbcTemplate jdbc,
            ObjectMapper mapper,
            ReportDestinationResolver destinations,
            PhaseIIAuditPublisher audit) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.destinations = destinations;
        this.audit = audit;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            @RequestBody Map<String, Object> body,
            Authentication authentication) {
        try {
            String cronValue = required(body, "cronExpression");
            CronExpression cron = CronExpression.parse(cronValue);
            DeliveryChannel channel = DeliveryChannel.valueOf(
                    required(body, "deliveryChannel").toUpperCase(java.util.Locale.ROOT));
            String timeZone = String.valueOf(body.getOrDefault("timeZone", "Asia/Vientiane"));
            ZoneId zoneId = ZoneId.of(timeZone);
            ZonedDateTime next = cron.next(ZonedDateTime.now(zoneId));
            if (next == null) {
                throw new IllegalArgumentException("Cron expression has no next execution");
            }

            Map<String, Object> destination = mapper.convertValue(
                    body.get("destination"),
                    new TypeReference<Map<String, Object>>() {});
            destinations.validateConfiguration(channel, destination);
            String destinationJson = mapper.writeValueAsString(destination);
            int retentionDays = integer(body.getOrDefault("retentionDays", 30), "retentionDays");
            if (retentionDays < 1 || retentionDays > 3650) {
                throw new IllegalArgumentException("retentionDays must be between 1 and 3650");
            }

            UUID id = UUID.randomUUID();
            int inserted = jdbc.update("""
                    INSERT INTO report_delivery_schedule(
                        id, code, report_type, recipient_participant_id,
                        cron_expression, time_zone, delivery_channel,
                        destination_config, retention_days, status,
                        next_run_at, created_by)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, 'ACTIVE', ?, ?)
                    """,
                    id,
                    required(body, "code"),
                    required(body, "reportType"),
                    required(body, "recipientParticipantId"),
                    cronValue,
                    timeZone,
                    channel.name(),
                    destinationJson,
                    retentionDays,
                    next.toOffsetDateTime(),
                    authentication == null ? "unknown" : authentication.getName());
            if (inserted != 1) {
                throw new IllegalStateException("Report schedule was not created");
            }
            audit.publish(
                    "report.schedule_created",
                    "REPORT_DELIVERY_SCHEDULE",
                    id.toString(),
                    authentication == null ? "unknown" : authentication.getName(),
                    Map.of(
                            "code", required(body, "code"),
                            "reportType", required(body, "reportType"),
                            "channel", channel.name(),
                            "recipient", required(body, "recipientParticipantId")));
            return ResponseEntity.status(201).body(Map.of(
                    "id", id,
                    "status", "ACTIVE",
                    "nextRunAt", next.toOffsetDateTime()));
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid report schedule", exception);
        }
    }

    @PatchMapping("/{id}/suspend")
    public Map<String, Object> suspend(
            @PathVariable UUID id,
            Authentication authentication) {
        int updated = jdbc.update(
                "UPDATE report_delivery_schedule SET status='SUSPENDED' WHERE id=? AND status='ACTIVE'",
                id);
        if (updated != 1) {
            throw new IllegalArgumentException("Active report schedule not found");
        }
        audit.publish(
                "report.schedule_suspended",
                "REPORT_DELIVERY_SCHEDULE",
                id.toString(),
                authentication == null ? "unknown" : authentication.getName(),
                Map.of("status", "SUSPENDED"));
        return Map.of("id", id, "status", "SUSPENDED");
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        return jdbc.queryForList("""
                SELECT id, code, report_type, recipient_participant_id,
                       cron_expression, time_zone, delivery_channel,
                       status, next_run_at
                  FROM report_delivery_schedule
                 ORDER BY code
                """);
    }

    private static String required(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new IllegalArgumentException("Missing " + key);
        }
        return String.valueOf(value).trim();
    }

    private static int integer(Object value, String name) {
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(name + " must be an integer", exception);
        }
    }
}
