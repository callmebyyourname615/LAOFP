package com.example.switching.paymentorchestration;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.ConnectionCallback;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.switching.common.PhaseIIAuditPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
@ConditionalOnProperty(
        prefix = "switching.phase-ii.push-payment-orchestrator",
        name = "enabled",
        havingValue = "true")
public class PushPaymentOrchestrator {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final PushPaymentPolicyRepository policies;
    private final Map<PaymentChannel, PaymentLifecycle> lifecycles;
    private final ObjectMapper mapper;
    private final PhaseIIAuditPublisher audit;

    public PushPaymentOrchestrator(
            JdbcTemplate jdbc,
            TransactionTemplate transactions,
            PushPaymentPolicyRepository policies,
            List<PaymentLifecycle> lifecycles,
            ObjectMapper mapper,
            PhaseIIAuditPublisher audit) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.policies = policies;
        this.mapper = mapper;
        this.audit = audit;
        EnumMap<PaymentChannel, PaymentLifecycle> byChannel =
                new EnumMap<>(PaymentChannel.class);
        for (PaymentLifecycle lifecycle : lifecycles) {
            if (byChannel.put(lifecycle.channel(), lifecycle) != null) {
                throw new IllegalStateException(
                        "Duplicate lifecycle for " + lifecycle.channel());
            }
        }
        this.lifecycles = Map.copyOf(byChannel);
    }

    public PushPaymentResult start(PushPaymentRequest request) {
        validate(request);
        PushPaymentPolicy policy = policies.active(request.channel());
        String requestHash = hash(request);
        String requestJson = json(request);
        Claim claim = transactions.execute(status ->
                claim(request, policy, requestHash, requestJson));
        if (claim == null) {
            throw new IllegalStateException("Unable to claim push-payment execution");
        }
        if (!claim.created()) {
            return claim.existing();
        }
        return execute(claim.executionId(), request, policy, 1, true);
    }

    public PushPaymentResult retry(UUID executionId) {
        RetryClaim claim = transactions.execute(status -> claimRetry(executionId));
        if (claim == null) {
            throw new IllegalStateException("Retry execution is unavailable");
        }
        return execute(
                executionId,
                claim.request(),
                claim.policy(),
                claim.attempt(),
                false);
    }

    private Claim claim(
            PushPaymentRequest request,
            PushPaymentPolicy policy,
            String requestHash,
            String requestJson) {
        String lockKey = request.channel().name() + "|" + request.idempotencyKey();
        lockIdempotency(lockKey);

        List<Map<String, Object>> existing = jdbc.queryForList("""
                SELECT id, request_sha256, status, external_reference,
                       idempotency_expires_at, result_payload, result_message
                  FROM push_payment_execution
                 WHERE channel=?
                   AND idempotency_key=?
                   AND idempotency_active=true
                 FOR UPDATE
                """, request.channel().name(), request.idempotencyKey());
        if (!existing.isEmpty()) {
            Map<String, Object> row = existing.getFirst();
            OffsetDateTime expiresAt = (OffsetDateTime) row.get("idempotency_expires_at");
            if (expiresAt != null && expiresAt.isAfter(OffsetDateTime.now())) {
                if (!requestHash.equals(row.get("request_sha256"))) {
                    throw new IllegalStateException(
                            "Idempotency key reused with different payload");
                }
                PaymentExecutionStatus existingStatus = PaymentExecutionStatus.valueOf(
                        String.valueOf(row.get("status")));
                Object restored = restoreChannelResult(
                        request.channel(),
                        row.get("result_payload"));
                if (restored == null
                        && (existingStatus == PaymentExecutionStatus.STARTED
                                || existingStatus == PaymentExecutionStatus.RETRY_SCHEDULED)) {
                    throw new IllegalStateException(
                            "An execution with this idempotency key is still in progress");
                }
                return new Claim(
                        (UUID) row.get("id"),
                        false,
                        new PushPaymentResult(
                                (UUID) row.get("id"),
                                (String) row.get("external_reference"),
                                existingStatus,
                                row.get("result_message") == null
                                        ? "Existing execution"
                                        : String.valueOf(row.get("result_message")),
                                restored));
            }
            jdbc.update("""
                    UPDATE push_payment_execution
                       SET idempotency_active=false,
                           updated_at=now()
                     WHERE id=?
                    """, row.get("id"));
        }

        UUID executionId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO push_payment_execution(
                    id, channel, business_reference, idempotency_key,
                    request_sha256, request_payload, policy_id, status,
                    attempt_count, idempotency_expires_at,
                    idempotency_active)
                VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, 'STARTED', 1, ?, true)
                """,
                executionId,
                request.channel().name(),
                request.businessReference(),
                request.idempotencyKey(),
                requestHash,
                requestJson,
                policy.id(),
                OffsetDateTime.now().plus(policy.idempotencyTtl()));
        transition(
                executionId,
                null,
                PaymentExecutionStatus.STARTED,
                "STARTED",
                Map.of("policyVersion", policy.version()));
        return new Claim(executionId, true, null);
    }

    private RetryClaim claimRetry(UUID executionId) {
        Map<String, Object> row = jdbc.queryForMap("""
                SELECT e.status, e.attempt_count, e.request_payload,
                       e.policy_id
                  FROM push_payment_execution e
                 WHERE e.id=?
                 FOR UPDATE OF e
                """, executionId);
        if (!"RETRY_SCHEDULED".equals(row.get("status"))) {
            throw new IllegalStateException("Execution is not scheduled for retry");
        }
        int attempt = ((Number) row.get("attempt_count")).intValue() + 1;
        PushPaymentRequest request;
        try {
            request = mapper.readValue(
                    String.valueOf(row.get("request_payload")),
                    PushPaymentRequest.class);
        } catch (Exception exception) {
            throw new IllegalStateException("Stored push-payment request is invalid", exception);
        }
        PushPaymentPolicy policy = policies.byId((UUID) row.get("policy_id"));
        jdbc.update("""
                UPDATE push_payment_execution
                   SET status='STARTED',
                       attempt_count=?,
                       next_attempt_at=NULL,
                       updated_at=now()
                 WHERE id=? AND status='RETRY_SCHEDULED'
                """, attempt, executionId);
        transition(
                executionId,
                PaymentExecutionStatus.RETRY_SCHEDULED,
                PaymentExecutionStatus.STARTED,
                "RETRY_STARTED",
                Map.of("attempt", attempt));
        return new RetryClaim(request, policy, attempt);
    }

    private PushPaymentResult execute(
            UUID executionId,
            PushPaymentRequest request,
            PushPaymentPolicy policy,
            int attempt,
            boolean propagateFailure) {
        PaymentLifecycle lifecycle = Optional.ofNullable(lifecycles.get(request.channel()))
                .orElseThrow(() -> new IllegalStateException(
                        "No lifecycle for " + request.channel()));
        try {
            PushPaymentResult lifecycleResult = lifecycle.execute(request, policy);
            PushPaymentResult result = new PushPaymentResult(
                    executionId,
                    lifecycleResult.externalReference(),
                    lifecycleResult.status(),
                    lifecycleResult.message(),
                    lifecycleResult.channelResult());
            transactions.executeWithoutResult(status -> complete(executionId, result));
            return result;
        } catch (RuntimeException exception) {
            PaymentExecutionStatus failureStatus = transactions.execute(status ->
                    recordFailure(executionId, policy, attempt, exception));
            if (failureStatus == null) {
                throw new IllegalStateException(
                        "Unable to record push-payment failure", exception);
            }
            if (propagateFailure) {
                throw exception;
            }
            return new PushPaymentResult(
                    executionId,
                    null,
                    failureStatus,
                    failureStatus == PaymentExecutionStatus.RETRY_SCHEDULED
                            ? "Retry attempt failed and was rescheduled"
                            : "Retry attempts exhausted");
        }
    }

    private void complete(UUID executionId, PushPaymentResult result) {
        PaymentExecutionStatus status = result.status();
        int updated = jdbc.update("""
                UPDATE push_payment_execution
                   SET status=?,
                       external_reference=?,
                       result_message=?,
                       result_payload=?::jsonb,
                       completed_at=CASE
                           WHEN ? IN ('SETTLED','REJECTED','FAILED') THEN now()
                           ELSE NULL
                       END,
                       last_error_code=NULL,
                       updated_at=now()
                 WHERE id=? AND status='STARTED'
                """,
                status.name(),
                result.externalReference(),
                result.message(),
                result.channelResult() == null ? null : json(result.channelResult()),
                status.name(),
                executionId);
        if (updated != 1) {
            throw new IllegalStateException("Push-payment execution claim was lost");
        }
        transition(
                executionId,
                PaymentExecutionStatus.STARTED,
                status,
                "LIFECYCLE_RESULT",
                Map.of("message", String.valueOf(result.message())));
        audit.publish(
                "push_payment." + status.name().toLowerCase(java.util.Locale.ROOT),
                "PUSH_PAYMENT_EXECUTION",
                executionId.toString(),
                "SYSTEM",
                Map.of(
                        "status", status.name(),
                        "externalReference", String.valueOf(result.externalReference())));
    }

    private PaymentExecutionStatus recordFailure(
            UUID executionId,
            PushPaymentPolicy policy,
            int attempt,
            RuntimeException exception) {
        boolean retry = attempt <= policy.retrySchedule().size();
        PaymentExecutionStatus status = retry
                ? PaymentExecutionStatus.RETRY_SCHEDULED
                : PaymentExecutionStatus.FAILED;
        OffsetDateTime nextAttempt = retry
                ? OffsetDateTime.now().plus(policy.retrySchedule().get(attempt - 1))
                : null;
        int updated = jdbc.update("""
                UPDATE push_payment_execution
                   SET status=?,
                       last_error_code=?,
                       next_attempt_at=?,
                       completed_at=CASE WHEN ?='FAILED' THEN now() ELSE NULL END,
                       updated_at=now()
                 WHERE id=? AND status='STARTED'
                """,
                status.name(),
                exception.getClass().getSimpleName(),
                nextAttempt,
                status.name(),
                executionId);
        if (updated == 1) {
            transition(
                    executionId,
                    PaymentExecutionStatus.STARTED,
                    status,
                    retry ? "RETRY_SCHEDULED" : "RETRY_EXHAUSTED",
                    Map.of(
                            "attempt", attempt,
                            "errorType", exception.getClass().getSimpleName()));
            return status;
        }
        throw new IllegalStateException("Push-payment execution claim was lost");
    }

    private void transition(
            UUID executionId,
            PaymentExecutionStatus from,
            PaymentExecutionStatus to,
            String reason,
            Map<String, Object> evidence) {
        jdbc.update("""
                INSERT INTO push_payment_transition(
                    id, execution_id, from_status, to_status,
                    reason_code, evidence)
                VALUES (?, ?, ?, ?, ?, ?::jsonb)
                """,
                UUID.randomUUID(),
                executionId,
                from == null ? null : from.name(),
                to.name(),
                reason,
                json(evidence));
    }

    private String hash(PushPaymentRequest request) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(json(request).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to serialize push-payment data", exception);
        }
    }

    private void lockIdempotency(String lockKey) {
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            try (java.sql.PreparedStatement statement = connection.prepareStatement(
                    "SELECT pg_advisory_xact_lock(hashtextextended(?, 0))")) {
                statement.setString(1, lockKey);
                statement.execute();
                return null;
            }
        });
    }

    private Object restoreChannelResult(
            PaymentChannel channel,
            Object storedPayload) {
        if (storedPayload == null) {
            return null;
        }
        PaymentLifecycle lifecycle = lifecycles.get(channel);
        if (lifecycle == null || lifecycle.channelResultType() == Object.class) {
            return null;
        }
        try {
            return mapper.readValue(
                    String.valueOf(storedPayload),
                    lifecycle.channelResultType());
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Stored channel result is invalid", exception);
        }
    }

    private static void validate(PushPaymentRequest request) {
        if (request == null
                || request.channel() == null
                || blank(request.businessReference())
                || blank(request.idempotencyKey())
                || request.payload() == null) {
            throw new IllegalArgumentException("Invalid push-payment request");
        }
        if (request.amount() != null && request.amount().signum() <= 0) {
            throw new IllegalArgumentException("Push-payment amount must be positive");
        }
        if ((request.channel() == PaymentChannel.TRANSFER
                || request.channel() == PaymentChannel.RTP
                || request.channel() == PaymentChannel.CROSS_BORDER)
                && (request.amount() == null || blank(request.currency()))) {
            throw new IllegalArgumentException(
                    "Amount and currency are required for the selected push-payment channel");
        }
        if (request.currency() != null
                && !request.currency().matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("Push-payment currency must be ISO-4217 format");
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private record Claim(
            UUID executionId,
            boolean created,
            PushPaymentResult existing) {}

    private record RetryClaim(
            PushPaymentRequest request,
            PushPaymentPolicy policy,
            int attempt) {}
}
