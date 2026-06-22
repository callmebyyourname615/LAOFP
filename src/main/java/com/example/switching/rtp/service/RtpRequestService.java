package com.example.switching.rtp.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.switching.rtp.config.RtpProperties;
import com.example.switching.rtp.dto.CancelRtpRequest;
import com.example.switching.rtp.dto.CreateRtpRequest;
import com.example.switching.rtp.dto.RtpCreateResult;
import com.example.switching.rtp.dto.RtpRequestResponse;
import com.example.switching.rtp.entity.RtpRequestEntity;
import com.example.switching.rtp.entity.RtpStateTransitionEntity;
import com.example.switching.rtp.enums.RtpStatus;
import com.example.switching.rtp.exception.RtpAccessDeniedException;
import com.example.switching.rtp.exception.RtpExpiryInvalidException;
import com.example.switching.rtp.exception.RtpIdempotencyConflictException;
import com.example.switching.rtp.exception.RtpNotFoundException;
import com.example.switching.rtp.repository.RtpRequestRepository;
import com.example.switching.rtp.repository.RtpStateTransitionRepository;

@Service
@ConditionalOnProperty(prefix = "switching.phase-ii.rtp", name = "enabled", havingValue = "true")
public class RtpRequestService {

    private static final String INSERT_REQUEST_SQL = """
            INSERT INTO rtp_request (
                id, request_correlation_id, request_fingerprint,
                payee_participant_id, payer_participant_id,
                payee_account, payer_account,
                requested_amount, authorised_amount, settled_amount,
                currency, description, status, expires_at, version
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING_AUTH', ?, 0)
            ON CONFLICT (payee_participant_id, request_correlation_id) DO NOTHING
            """;

    private final RtpRequestRepository requestRepository;
    private final RtpStateTransitionRepository transitionRepository;
    private final RtpStateMachine stateMachine;
    private final RtpRequestFingerprint fingerprint;
    private final RtpProperties properties;
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;
    private final RtpDomainEventPublisher events;

    public RtpRequestService(
            RtpRequestRepository requestRepository,
            RtpStateTransitionRepository transitionRepository,
            RtpStateMachine stateMachine,
            RtpRequestFingerprint fingerprint,
            RtpProperties properties,
            JdbcTemplate jdbcTemplate,
            @Qualifier("rtpClock") Clock clock,
            RtpDomainEventPublisher events) {
        this.requestRepository = requestRepository;
        this.transitionRepository = transitionRepository;
        this.stateMachine = stateMachine;
        this.fingerprint = fingerprint;
        this.properties = properties;
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
        this.events = events;
    }

    @Transactional
    public RtpCreateResult create(CreateRtpRequest request, RtpActor actor) {
        assertCanCreate(actor, request.payeeParticipantId());

        String correlationId = normalizedRequired(request.requestCorrelationId());
        String payeeParticipantId = normalizedRequired(request.payeeParticipantId());
        String requestFingerprint = fingerprint.sha256(request);
        Instant now = clock.instant();
        Instant expiresAt = resolveExpiry(request, now);
        UUID id = UUID.randomUUID();

        int inserted = jdbcTemplate.update(
                INSERT_REQUEST_SQL,
                id,
                correlationId,
                requestFingerprint,
                payeeParticipantId,
                normalizedRequired(request.payerParticipantId()),
                normalizedRequired(request.payeeAccount()),
                normalizedNullable(request.payerAccount()),
                request.requestedAmount(),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                normalizedRequired(request.currency()).toUpperCase(Locale.ROOT),
                normalizedNullable(request.description()),
                OffsetDateTime.ofInstant(expiresAt, ZoneOffset.UTC));

        RtpRequestEntity entity = requestRepository
                .findByPayeeParticipantIdAndRequestCorrelationId(payeeParticipantId, correlationId)
                .orElseThrow(() -> new IllegalStateException("RTP insert completed without a readable row"));

        if (inserted == 0) {
            assertCanRead(actor, entity);
            if (!requestFingerprint.equals(entity.getRequestFingerprint())) {
                throw new RtpIdempotencyConflictException(correlationId);
            }
            return new RtpCreateResult(toResponse(entity), false);
        }

        recordTransition(entity.getId(), null, RtpStatus.PENDING_AUTH, actor.actorId(), "RTP request created");
        events.publish("rtp.created", entity, java.util.Map.of());
        return new RtpCreateResult(toResponse(entity), true);
    }

    @Transactional(readOnly = true)
    public RtpRequestResponse get(UUID id, RtpActor actor) {
        RtpRequestEntity entity = requestRepository.findById(id)
                .orElseThrow(() -> new RtpNotFoundException(id));
        assertCanRead(actor, entity);
        return toResponse(entity);
    }

    @Transactional
    public RtpRequestResponse cancel(UUID id, CancelRtpRequest request, RtpActor actor) {
        RtpRequestEntity entity = requestRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new RtpNotFoundException(id));
        assertCanRead(actor, entity);

        if (entity.getStatus() == RtpStatus.CANCELLED) {
            return toResponse(entity);
        }

        stateMachine.assertTransition(entity.getStatus(), RtpStatus.CANCELLED);
        RtpStatus previous = entity.getStatus();
        String reason = request == null || normalizedNullable(request.reason()) == null
                ? "Cancelled by participant"
                : normalizedNullable(request.reason());

        entity.setStatus(RtpStatus.CANCELLED);
        entity.setCancellationReason(reason);
        entity.setCancelledAt(clock.instant());
        RtpRequestEntity saved = requestRepository.saveAndFlush(entity);
        recordTransition(saved.getId(), previous, RtpStatus.CANCELLED, actor.actorId(), reason);
        events.publish("rtp.cancelled", saved, java.util.Map.of("reason", reason));
        return toResponse(saved);
    }

    private Instant resolveExpiry(CreateRtpRequest request, Instant now) {
        Instant expiresAt;
        if (request.expiresAt() == null) {
            expiresAt = now.plus(properties.resolveExpiry(normalizedRequired(request.payeeParticipantId())));
        } else {
            expiresAt = request.expiresAt();
        }

        if (!expiresAt.isAfter(now)) {
            throw new RtpExpiryInvalidException("RTP expiry must be in the future");
        }

        Duration maximumExpiry = properties.getMaximumExpiry();
        if (maximumExpiry == null || maximumExpiry.isZero() || maximumExpiry.isNegative()) {
            throw new IllegalStateException("RTP maximum expiry must be positive");
        }
        Duration requestedTtl = Duration.between(now, expiresAt);
        if (requestedTtl.compareTo(maximumExpiry) > 0) {
            throw new RtpExpiryInvalidException("RTP expiry exceeds the configured maximum");
        }
        return expiresAt;
    }

    private void recordTransition(
            UUID requestId,
            RtpStatus from,
            RtpStatus to,
            String actorId,
            String reason) {
        RtpStateTransitionEntity transition = new RtpStateTransitionEntity();
        transition.setId(UUID.randomUUID());
        transition.setRequestId(requestId);
        transition.setFromStatus(from);
        transition.setToStatus(to);
        transition.setActorId(actorId == null || actorId.isBlank() ? "unknown" : actorId);
        transition.setReason(reason);
        transitionRepository.save(transition);
    }

    private void assertCanCreate(RtpActor actor, String payeeParticipantId) {
        if (actor != null && actor.privileged()) {
            return;
        }
        String actorParticipantId = actor == null ? null : normalizedNullable(actor.participantId());
        if (actorParticipantId == null
                || !normalizedRequired(payeeParticipantId).equals(actorParticipantId)) {
            throw new RtpAccessDeniedException();
        }
    }

    private void assertCanRead(RtpActor actor, RtpRequestEntity entity) {
        if (actor != null && actor.privileged()) {
            return;
        }
        String participantId = actor == null ? null : normalizedNullable(actor.participantId());
        if (participantId == null
                || (!participantId.equals(entity.getPayeeParticipantId())
                && !participantId.equals(entity.getPayerParticipantId()))) {
            throw new RtpAccessDeniedException();
        }
    }

    private static RtpRequestResponse toResponse(RtpRequestEntity entity) {
        return new RtpRequestResponse(
                entity.getId(),
                entity.getRequestCorrelationId(),
                entity.getPayeeParticipantId(),
                entity.getPayerParticipantId(),
                entity.getPayeeAccount(),
                entity.getPayerAccount(),
                entity.getRequestedAmount(),
                entity.getAuthorisedAmount(),
                entity.getSettledAmount(),
                entity.getCurrency(),
                entity.getDescription(),
                entity.getStatus(),
                entity.getAuthorisationMode(),
                entity.getExpiresAt(),
                entity.getTransferReference(),
                entity.getSettlementReference(),
                entity.getDeclineReason(),
                entity.getDeclinedAt(),
                entity.getAuthorisedAt(),
                entity.getSettledAt(),
                entity.getCancellationReason(),
                entity.getCancelledAt(),
                entity.getCreatedAt(),
                entity.getVersion());
    }

    private static String normalizedRequired(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Required RTP value must not be blank");
        }
        return value.trim();
    }

    private static String normalizedNullable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
