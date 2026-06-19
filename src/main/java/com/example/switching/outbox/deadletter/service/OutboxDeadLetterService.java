package com.example.switching.outbox.deadletter.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.example.switching.audit.service.AuditLogService;
import com.example.switching.outbox.deadletter.dto.DeadLetterResponse;
import com.example.switching.outbox.deadletter.entity.DeadLetterStatus;
import com.example.switching.outbox.deadletter.entity.OutboxDeadLetterEntity;
import com.example.switching.outbox.deadletter.repository.OutboxDeadLetterRepository;
import com.example.switching.outbox.queue.OutboxQueueMessage;
import com.example.switching.outbox.queue.OutboxQueueProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class OutboxDeadLetterService {

    private final OutboxDeadLetterRepository repository;
    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, OutboxQueueMessage> kafkaTemplate;
    private final OutboxQueueProperties queueProperties;
    private final AuditLogService auditLogService;

    public OutboxDeadLetterService(OutboxDeadLetterRepository repository,
                                   ObjectMapper objectMapper,
                                   KafkaTemplate<String, OutboxQueueMessage> kafkaTemplate,
                                   OutboxQueueProperties queueProperties,
                                   AuditLogService auditLogService) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.kafkaTemplate = kafkaTemplate;
        this.queueProperties = queueProperties;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public OutboxDeadLetterEntity quarantine(OutboxQueueMessage message, Throwable failure) {
        String eventId = message != null && StringUtils.hasText(message.eventId())
                ? message.eventId() : UUID.randomUUID().toString();
        String payload = serialize(message);
        LocalDateTime now = LocalDateTime.now();
        OutboxDeadLetterEntity entity = repository.findByEventId(eventId).orElseGet(() -> {
            OutboxDeadLetterEntity fresh = new OutboxDeadLetterEntity();
            fresh.setEventId(eventId);
            fresh.setFirstFailedAt(now);
            fresh.setFailureCount(0);
            return fresh;
        });
        entity.setSchemaName(message == null ? null : message.schemaName());
        entity.setSchemaVersion(message == null ? null : message.schemaVersion());
        entity.setOutboxEventId(message == null ? null : message.outboxEventId());
        entity.setTransferRef(message == null ? null : message.transferRef());
        entity.setPayloadJson(payload);
        entity.setPayloadSha256(sha256(payload));
        entity.setFailureType(failure == null ? "UnknownFailure" : failure.getClass().getSimpleName());
        entity.setFailureMessage(truncate(failure == null ? null : failure.getMessage(), 1000));
        entity.setStatus(DeadLetterStatus.QUARANTINED);
        entity.setFailureCount(entity.getFailureCount() + 1);
        entity.setLastFailedAt(now);
        OutboxDeadLetterEntity saved = repository.save(entity);
        auditLogService.log("OUTBOX_DEAD_LETTER_QUARANTINED", "OUTBOX_DEAD_LETTER",
                saved.getId().toString(), "SYSTEM",
                new AuditPayload(saved.getEventId(), saved.getOutboxEventId(), saved.getFailureType(), saved.getPayloadSha256()));
        return saved;
    }

    @Transactional(readOnly = true)
    public List<DeadLetterResponse> list(DeadLetterStatus status, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        return repository.findByStatusOrderByLastFailedAtDesc(status, PageRequest.of(0, safeLimit))
                .stream().map(DeadLetterResponse::from).toList();
    }

    @Transactional
    public DeadLetterResponse requestReplay(Long id, String actor) {
        OutboxDeadLetterEntity entity = locked(id);
        require(entity.getStatus() == DeadLetterStatus.QUARANTINED, "only quarantined records can request replay");
        entity.setStatus(DeadLetterStatus.REPLAY_REQUESTED);
        entity.setReplayRequestedBy(requireActor(actor));
        entity.setReplayRequestedAt(LocalDateTime.now());
        auditLogService.log("OUTBOX_DEAD_LETTER_REPLAY_REQUESTED", "OUTBOX_DEAD_LETTER", id.toString(), actor,
                new ActionPayload(entity.getEventId(), "REPLAY_REQUESTED"));
        return DeadLetterResponse.from(repository.save(entity));
    }

    @Transactional
    public DeadLetterResponse approveReplay(Long id, String actor) {
        OutboxDeadLetterEntity entity = locked(id);
        require(entity.getStatus() == DeadLetterStatus.REPLAY_REQUESTED, "replay request is not pending approval");
        String approver = requireActor(actor);
        require(!approver.equals(entity.getReplayRequestedBy()), "requester cannot approve own replay");
        entity.setStatus(DeadLetterStatus.APPROVED);
        entity.setReplayApprovedBy(approver);
        entity.setReplayApprovedAt(LocalDateTime.now());
        auditLogService.log("OUTBOX_DEAD_LETTER_REPLAY_APPROVED", "OUTBOX_DEAD_LETTER", id.toString(), approver,
                new ActionPayload(entity.getEventId(), "APPROVED"));
        return DeadLetterResponse.from(repository.save(entity));
    }

    @Transactional
    public DeadLetterResponse executeReplay(Long id, String actor) {
        OutboxDeadLetterEntity entity = locked(id);
        require(entity.getStatus() == DeadLetterStatus.APPROVED, "replay is not approved");
        OutboxQueueMessage message = deserialize(entity.getPayloadJson());
        require(sha256(entity.getPayloadJson()).equals(entity.getPayloadSha256()), "dead-letter payload integrity check failed");
        try {
            kafkaTemplate.send(queueProperties.topic(), key(message), message).get(10, TimeUnit.SECONDS);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to publish approved replay", ex);
        }
        entity.setStatus(DeadLetterStatus.REPLAYED);
        entity.setReplayedBy(requireActor(actor));
        entity.setReplayedAt(LocalDateTime.now());
        auditLogService.log("OUTBOX_DEAD_LETTER_REPLAYED", "OUTBOX_DEAD_LETTER", id.toString(), actor,
                new ActionPayload(entity.getEventId(), "REPLAYED"));
        return DeadLetterResponse.from(repository.save(entity));
    }

    @Transactional
    public DeadLetterResponse discard(Long id, String actor) {
        OutboxDeadLetterEntity entity = locked(id);
        require(entity.getStatus() != DeadLetterStatus.REPLAYED, "replayed record cannot be discarded");
        entity.setStatus(DeadLetterStatus.DISCARDED);
        entity.setDiscardedBy(requireActor(actor));
        entity.setDiscardedAt(LocalDateTime.now());
        auditLogService.log("OUTBOX_DEAD_LETTER_DISCARDED", "OUTBOX_DEAD_LETTER", id.toString(), actor,
                new ActionPayload(entity.getEventId(), "DISCARDED"));
        return DeadLetterResponse.from(repository.save(entity));
    }

    private OutboxDeadLetterEntity locked(Long id) {
        return repository.findByIdForUpdate(id).orElseThrow(() -> new IllegalArgumentException("dead letter not found: " + id));
    }

    private String serialize(OutboxQueueMessage message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to serialize dead-letter payload", ex);
        }
    }

    private OutboxQueueMessage deserialize(String payload) {
        try {
            return objectMapper.readValue(payload, OutboxQueueMessage.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to deserialize dead-letter payload", ex);
        }
    }

    private static String key(OutboxQueueMessage message) {
        return message.outboxEventId() == null ? message.eventId() : message.outboxEventId().toString();
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private static String requireActor(String actor) {
        if (!StringUtils.hasText(actor)) {
            throw new IllegalArgumentException("authenticated actor is required");
        }
        return actor.trim();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }

    private record AuditPayload(String eventId, Long outboxEventId, String failureType, String payloadSha256) {}
    private record ActionPayload(String eventId, String action) {}
}
