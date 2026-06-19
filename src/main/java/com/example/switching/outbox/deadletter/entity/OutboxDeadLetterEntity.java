package com.example.switching.outbox.deadletter.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "outbox_dead_letters")
@Getter
@Setter
public class OutboxDeadLetterEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "event_id", nullable = false, unique = true, length = 36)
    private String eventId;
    @Column(name = "schema_name", length = 160)
    private String schemaName;
    @Column(name = "schema_version")
    private Integer schemaVersion;
    @Column(name = "outbox_event_id")
    private Long outboxEventId;
    @Column(name = "transfer_ref", length = 128)
    private String transferRef;
    @Column(name = "payload_json", nullable = false, columnDefinition = "TEXT")
    private String payloadJson;
    @Column(name = "payload_sha256", nullable = false, length = 64)
    private String payloadSha256;
    @Column(name = "failure_type", nullable = false, length = 160)
    private String failureType;
    @Column(name = "failure_message", length = 1000)
    private String failureMessage;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private DeadLetterStatus status;
    @Column(name = "failure_count", nullable = false)
    private int failureCount;
    @Column(name = "first_failed_at", nullable = false)
    private LocalDateTime firstFailedAt;
    @Column(name = "last_failed_at", nullable = false)
    private LocalDateTime lastFailedAt;
    @Column(name = "replay_requested_by", length = 160)
    private String replayRequestedBy;
    @Column(name = "replay_requested_at")
    private LocalDateTime replayRequestedAt;
    @Column(name = "replay_approved_by", length = 160)
    private String replayApprovedBy;
    @Column(name = "replay_approved_at")
    private LocalDateTime replayApprovedAt;
    @Column(name = "replayed_by", length = 160)
    private String replayedBy;
    @Column(name = "replayed_at")
    private LocalDateTime replayedAt;
    @Column(name = "discarded_by", length = 160)
    private String discardedBy;
    @Column(name = "discarded_at")
    private LocalDateTime discardedAt;
    @Version
    @Column(name = "version", nullable = false)
    private long version;
}
