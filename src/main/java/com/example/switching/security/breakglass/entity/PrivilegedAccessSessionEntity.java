package com.example.switching.security.breakglass.entity;

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
@Table(name = "privileged_access_sessions")
@Getter
@Setter
public class PrivilegedAccessSessionEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "session_ref", nullable = false, unique = true, length = 64)
    private String sessionRef;
    @Column(name = "requested_by", nullable = false, length = 160)
    private String requestedBy;
    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;
    @Column(name = "reason", nullable = false, length = 1000)
    private String reason;
    @Column(name = "ticket_reference", nullable = false, length = 160)
    private String ticketReference;
    @Column(name = "requested_ttl_minutes", nullable = false)
    private int requestedTtlMinutes;
    @Column(name = "max_uses", nullable = false)
    private int maxUses;
    @Column(name = "approved_by", length = 160)
    private String approvedBy;
    @Column(name = "approved_at")
    private LocalDateTime approvedAt;
    @Column(name = "token_hash", unique = true, length = 64)
    private String tokenHash;
    @Column(name = "token_prefix", length = 12)
    private String tokenPrefix;
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;
    @Column(name = "use_count", nullable = false)
    private int useCount;
    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private PrivilegedAccessStatus status;
    @Column(name = "revoked_by", length = 160)
    private String revokedBy;
    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;
    @Version
    @Column(name = "version", nullable = false)
    private long version;
}
