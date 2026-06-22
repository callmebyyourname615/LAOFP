package com.example.switching.reportdelivery;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.switching.webhook.service.WebhookEventPublisher;
import com.example.switching.common.PhaseIIAuditPublisher;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
@Profile("!migration")
@ConditionalOnProperty(
        prefix = "switching.phase-ii.report-delivery",
        name = "enabled",
        havingValue = "true")
public class ReportDeliveryService {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final ReportArtifactService artifacts;
    private final Map<DeliveryChannel, ReportDeliveryChannel> channels;
    private final ObjectMapper mapper;
    private final WebhookEventPublisher webhook;
    private final PhaseIIAuditPublisher signedAudit;

    public ReportDeliveryService(
            JdbcTemplate jdbc,
            TransactionTemplate transactions,
            ReportArtifactService artifacts,
            List<ReportDeliveryChannel> channels,
            ObjectMapper mapper,
            WebhookEventPublisher webhook,
            PhaseIIAuditPublisher signedAudit) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.artifacts = artifacts;
        this.mapper = mapper;
        this.webhook = webhook;
        this.signedAudit = signedAudit;
        EnumMap<DeliveryChannel, ReportDeliveryChannel> byChannel =
                new EnumMap<>(DeliveryChannel.class);
        for (ReportDeliveryChannel channel : channels) {
            ReportDeliveryChannel previous = byChannel.put(channel.channel(), channel);
            if (previous != null) {
                throw new IllegalStateException(
                        "Duplicate report delivery channel " + channel.channel());
            }
        }
        this.channels = Map.copyOf(byChannel);
    }

    @Scheduled(fixedDelayString = "${switching.phase-ii.report-delivery.poll-ms:60000}")
    public void poll() {
        queueDueSchedules();
        recoverStaleClaims();
        for (Map<String, Object> run : claimDueRuns()) {
            deliverClaimed(run);
        }
    }

    void queueDueSchedules() {
        transactions.executeWithoutResult(status -> {
            List<Map<String, Object>> due = jdbc.queryForList("""
                    SELECT *
                      FROM report_delivery_schedule
                     WHERE status='ACTIVE'
                       AND next_run_at IS NOT NULL
                       AND next_run_at <= now()
                     ORDER BY next_run_at
                     LIMIT 100
                     FOR UPDATE SKIP LOCKED
                    """);
            OffsetDateTime now = OffsetDateTime.now();
            for (Map<String, Object> schedule : due) {
                UUID scheduleId = (UUID) schedule.get("id");
                OffsetDateTime scheduledFor = (OffsetDateTime) schedule.get("next_run_at");
                jdbc.update("""
                        INSERT INTO report_delivery_run(
                            id, schedule_id, scheduled_for, status, next_attempt_at)
                        VALUES (?, ?, ?, 'QUEUED', ?)
                        ON CONFLICT(schedule_id, scheduled_for) DO NOTHING
                        """, UUID.randomUUID(), scheduleId, scheduledFor, now);

                OffsetDateTime nextRun = nextRun(schedule, scheduledFor, now);
                jdbc.update(
                        "UPDATE report_delivery_schedule SET next_run_at=? WHERE id=?",
                        nextRun,
                        scheduleId);
            }
        });
    }

    void recoverStaleClaims() {
        jdbc.update("""
                UPDATE report_delivery_run
                   SET status='RETRY',
                       next_attempt_at=now(),
                       last_error_code='STALE_DELIVERY_CLAIM',
                       updated_at=now()
                 WHERE status='DELIVERING'
                   AND updated_at < now() - interval '15 minutes'
                """);
    }

    List<Map<String, Object>> claimDueRuns() {
        List<Map<String, Object>> claimed = transactions.execute(status -> {
            List<Map<String, Object>> due = jdbc.queryForList("""
                    SELECT r.id AS run_id,
                           r.scheduled_for,
                           r.attempt_count,
                           s.id AS schedule_id,
                           s.report_type,
                           s.recipient_participant_id,
                           s.delivery_channel,
                           s.destination_config,
                           s.retention_days
                      FROM report_delivery_run r
                      JOIN report_delivery_schedule s ON s.id=r.schedule_id
                     WHERE r.status IN ('QUEUED','RETRY')
                       AND (r.next_attempt_at IS NULL OR r.next_attempt_at<=now())
                     ORDER BY r.created_at
                     LIMIT 100
                     FOR UPDATE OF r SKIP LOCKED
                    """);
            List<Map<String, Object>> result = new ArrayList<>();
            for (Map<String, Object> row : due) {
                UUID runId = (UUID) row.get("run_id");
                int attempt = ((Number) row.get("attempt_count")).intValue() + 1;
                int updated = jdbc.update("""
                        UPDATE report_delivery_run
                           SET status='DELIVERING',
                               attempt_count=?,
                               updated_at=now()
                         WHERE id=?
                           AND status IN ('QUEUED','RETRY')
                        """, attempt, runId);
                if (updated == 1) {
                    java.util.HashMap<String, Object> copy = new java.util.HashMap<>(row);
                    copy.put("attempt_count", attempt);
                    result.add(Map.copyOf(copy));
                }
            }
            return List.copyOf(result);
        });
        return claimed == null ? List.of() : claimed;
    }

    void deliverClaimed(Map<String, Object> run) {
        UUID runId = (UUID) run.get("run_id");
        int attempt = ((Number) run.get("attempt_count")).intValue();
        try {
            String generationKey = ((OffsetDateTime) run.get("scheduled_for"))
                    .toInstant().toString();
            ReportArtifactService.StoredArtifact artifact = artifacts.getOrGenerate(
                    String.valueOf(run.get("report_type")),
                    String.valueOf(run.get("recipient_participant_id")),
                    generationKey,
                    ((Number) run.get("retention_days")).intValue());
            Map<String, Object> destination = mapper.readValue(
                    String.valueOf(run.get("destination_config")),
                    new TypeReference<Map<String, Object>>() {});
            DeliveryChannel channel = DeliveryChannel.valueOf(
                    String.valueOf(run.get("delivery_channel")));
            ReportDeliveryChannel deliveryChannel = Optional.ofNullable(channels.get(channel))
                    .orElseThrow(() -> new IllegalStateException(
                            "No delivery implementation for " + channel));
            ReportDeliveryChannel.DeliveryResult result =
                    deliveryChannel.deliver(artifact, destination);

            transactions.executeWithoutResult(status -> {
                int updated = jdbc.update("""
                        UPDATE report_delivery_run
                           SET artifact_id=?,
                               status='DELIVERED',
                               delivered_at=now(),
                               remote_reference=?,
                               last_error_code=NULL,
                               next_attempt_at=NULL,
                               updated_at=now()
                         WHERE id=? AND status='DELIVERING'
                        """, artifact.id(), result.remoteReference(), runId);
                if (updated != 1) {
                    throw new IllegalStateException("Report delivery claim was lost");
                }
                audit(runId, "report.delivered", Map.of(
                        "artifactId", artifact.id(),
                        "sha256", artifact.sha256(),
                        "remoteReference", result.remoteReference()));
                webhook.publishQuietly(
                        "report.delivered",
                        String.valueOf(run.get("recipient_participant_id")),
                        runId.toString(),
                        Map.of(
                                "artifactId", artifact.id().toString(),
                                "sha256", artifact.sha256(),
                                "channel", channel.name()));
                signedAudit.publish(
                        "report.delivered",
                        "REPORT_DELIVERY_RUN",
                        runId.toString(),
                        "SYSTEM",
                        Map.of(
                                "artifactId", artifact.id().toString(),
                                "sha256", artifact.sha256(),
                                "channel", channel.name(),
                                "remoteReference", result.remoteReference()));
            });
        } catch (Exception exception) {
            transactions.executeWithoutResult(status -> markFailed(
                    runId,
                    attempt,
                    exception.getClass().getSimpleName()));
        }
    }

    private void markFailed(UUID runId, int attempt, String errorCode) {
        String nextStatus = attempt >= 5 ? "DEAD" : "RETRY";
        long backoffSeconds = Math.min(3600, 30L << Math.min(6, Math.max(0, attempt - 1)));
        int updated = jdbc.update("""
                UPDATE report_delivery_run
                   SET status=?,
                       last_error_code=?,
                       next_attempt_at=?,
                       updated_at=now()
                 WHERE id=? AND status='DELIVERING'
                """,
                nextStatus,
                errorCode,
                "DEAD".equals(nextStatus)
                        ? null
                        : OffsetDateTime.now().plusSeconds(backoffSeconds),
                runId);
        if (updated == 1) {
            Map<String, Object> evidence = Map.of(
                    "attempt", attempt,
                    "error", errorCode,
                    "nextStatus", nextStatus);
            audit(runId, "report.delivery_failed", evidence);
            signedAudit.publish(
                    "report.delivery_failed",
                    "REPORT_DELIVERY_RUN",
                    runId.toString(),
                    "SYSTEM",
                    evidence);
        }
    }

    private OffsetDateTime nextRun(
            Map<String, Object> schedule,
            OffsetDateTime scheduledFor,
            OffsetDateTime now) {
        CronExpression cron = CronExpression.parse(
                String.valueOf(schedule.get("cron_expression")));
        ZoneId zone = ZoneId.of(String.valueOf(schedule.get("time_zone")));
        ZonedDateTime cursor = scheduledFor.atZoneSameInstant(zone);
        ZonedDateTime next = cron.next(cursor);
        for (int iteration = 0;
                next != null && !next.toOffsetDateTime().isAfter(now) && iteration < 10_000;
                iteration++) {
            next = cron.next(next);
        }
        if (next == null || !next.toOffsetDateTime().isAfter(now)) {
            throw new IllegalStateException("Unable to calculate the next report run");
        }
        return next.toOffsetDateTime();
    }

    private void audit(UUID runId, String eventType, Map<String, Object> payload) {
        try {
            String json = mapper.writeValueAsString(payload);
            jdbc.update("""
                    INSERT INTO report_delivery_audit(
                        id, run_id, event_type, event_payload, payload_sha256)
                    VALUES (?, ?, ?, ?::jsonb, ?)
                    """,
                    UUID.randomUUID(),
                    runId,
                    eventType,
                    json,
                    sha256(json));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to persist report delivery audit", exception);
        }
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
}
