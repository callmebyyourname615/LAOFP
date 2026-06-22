package com.example.switching.paymentorchestration;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.switching.common.PhaseIIAuditPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class PolicyManagementService {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final PhaseIIAuditPublisher audit;

    public PolicyManagementService(
            JdbcTemplate jdbc,
            ObjectMapper mapper,
            PhaseIIAuditPublisher audit) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.audit = audit;
    }

    @Transactional
    public UUID createDraft(Map<String, Object> command, String actor) {
        try {
            PaymentChannel channel = PaymentChannel.valueOf(
                    required(command, "channel").toUpperCase(Locale.ROOT));
            int version = jdbc.queryForObject("""
                    SELECT coalesce(max(policy_version), 0) + 1
                      FROM push_payment_policy
                     WHERE channel=?
                    """, Integer.class, channel.name());
            long timeoutMs = longValue(command.getOrDefault("timeoutMs", 30_000), "timeoutMs");
            long ttlSeconds = longValue(
                    command.getOrDefault("idempotencyTtlSeconds", 86_400),
                    "idempotencyTtlSeconds");
            FinalityMode finality = FinalityMode.valueOf(
                    String.valueOf(command.getOrDefault("finalityMode", "ASYNCHRONOUS"))
                            .toUpperCase(Locale.ROOT));
            String retryArray = postgresIntegerArray(command.get("retryScheduleSeconds"));
            String webhookEvents = mapper.writeValueAsString(
                    command.getOrDefault("webhookEvents", Map.of()));

            UUID id = UUID.randomUUID();
            jdbc.update("""
                    INSERT INTO push_payment_policy(
                        id, channel, policy_version, status, timeout_ms,
                        retry_schedule_seconds, finality_mode,
                        webhook_event_names, idempotency_ttl_seconds,
                        valid_from, requested_by)
                    VALUES (?, ?, ?, 'DRAFT', ?, ?::integer[], ?, ?::jsonb, ?, ?, ?)
                    """,
                    id,
                    channel.name(),
                    version,
                    timeoutMs,
                    retryArray,
                    finality.name(),
                    webhookEvents,
                    ttlSeconds,
                    OffsetDateTime.now(),
                    actor);
            audit.publish(
                    "push_payment.policy_created",
                    "PUSH_PAYMENT_POLICY",
                    id.toString(),
                    actor,
                    Map.of("channel", channel.name(), "version", version));
            return id;
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid push-payment policy", exception);
        }
    }

    @Transactional
    public void activate(UUID id, String approver) {
        Map<String, Object> row = jdbc.queryForMap("""
                SELECT channel, requested_by, status, policy_version
                  FROM push_payment_policy
                 WHERE id=?
                 FOR UPDATE
                """, id);
        if (approver == null || approver.isBlank()) {
            throw new IllegalArgumentException("Approver is required");
        }
        if (approver.equals(row.get("requested_by"))) {
            throw new IllegalArgumentException("Requester cannot approve own policy");
        }
        if (!"DRAFT".equals(row.get("status"))) {
            throw new IllegalStateException("Policy is not DRAFT");
        }
        jdbc.update(
                "UPDATE push_payment_policy SET status='RETIRED', valid_until=now() "
                        + "WHERE channel=? AND status='ACTIVE'",
                row.get("channel"));
        int activated = jdbc.update("""
                UPDATE push_payment_policy
                   SET status='ACTIVE', approved_by=?, valid_from=now()
                 WHERE id=? AND status='DRAFT'
                """, approver, id);
        if (activated != 1) {
            throw new IllegalStateException("Policy activation lost its update lock");
        }
        audit.publish(
                "push_payment.policy_activated",
                "PUSH_PAYMENT_POLICY",
                id.toString(),
                approver,
                Map.of(
                        "channel", String.valueOf(row.get("channel")),
                        "version", row.get("policy_version")));
    }

    private static String required(Map<String, Object> command, String key) {
        Object value = command.get(key);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new IllegalArgumentException("Missing " + key);
        }
        return String.valueOf(value).trim();
    }

    private static long longValue(Object value, String key) {
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(key + " must be numeric", exception);
        }
    }

    private static String postgresIntegerArray(Object raw) {
        if (raw == null) {
            return "{30,60,120}";
        }
        if (!(raw instanceof List<?> values)) {
            throw new IllegalArgumentException("retryScheduleSeconds must be an array");
        }
        return values.stream()
                .map(value -> {
                    long seconds = longValue(value, "retryScheduleSeconds");
                    if (seconds < 1 || seconds > 86_400) {
                        throw new IllegalArgumentException(
                                "retryScheduleSeconds values must be between 1 and 86400");
                    }
                    return Long.toString(seconds);
                })
                .collect(Collectors.joining(",", "{", "}"));
    }
}
