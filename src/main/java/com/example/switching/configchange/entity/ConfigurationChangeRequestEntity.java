package com.example.switching.configchange.entity;

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
@Table(name = "configuration_change_requests")
@Getter
@Setter
public class ConfigurationChangeRequestEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "request_ref", nullable = false, unique = true, length = 64)
    private String requestRef;
    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 48)
    private ConfigurationTargetType targetType;
    @Column(name = "target_key", nullable = false, length = 160)
    private String targetKey;
    @Column(name = "previous_value", nullable = false, length = 512)
    private String previousValue;
    @Column(name = "desired_value", nullable = false, length = 512)
    private String desiredValue;
    @Column(name = "payload_sha256", nullable = false, length = 64)
    private String payloadSha256;
    @Column(name = "reason", nullable = false, length = 1000)
    private String reason;
    @Column(name = "ticket_reference", nullable = false, length = 160)
    private String ticketReference;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private ConfigurationChangeStatus status;
    @Column(name = "requested_by", nullable = false, length = 160)
    private String requestedBy;
    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;
    @Column(name = "approved_by", length = 160)
    private String approvedBy;
    @Column(name = "approved_at")
    private LocalDateTime approvedAt;
    @Column(name = "executed_by", length = 160)
    private String executedBy;
    @Column(name = "executed_at")
    private LocalDateTime executedAt;
    @Column(name = "rejected_by", length = 160)
    private String rejectedBy;
    @Column(name = "rejected_at")
    private LocalDateTime rejectedAt;
    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;
    @Version
    @Column(name = "version", nullable = false)
    private long version;
}
