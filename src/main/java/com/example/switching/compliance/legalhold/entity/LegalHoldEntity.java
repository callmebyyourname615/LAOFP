package com.example.switching.compliance.legalhold.entity;

import java.time.LocalDate;
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
@Table(name = "legal_holds")
@Getter
@Setter
public class LegalHoldEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "hold_ref", nullable = false, unique = true, length = 64)
    private String holdRef;
    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false, length = 24)
    private LegalHoldScopeType scopeType;
    @Column(name = "scope_key", nullable = false, length = 160)
    private String scopeKey;
    @Column(name = "effective_from")
    private LocalDate effectiveFrom;
    @Column(name = "effective_to")
    private LocalDate effectiveTo;
    @Column(name = "reason", nullable = false, length = 1000)
    private String reason;
    @Column(name = "case_reference", nullable = false, length = 160)
    private String caseReference;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private LegalHoldStatus status;
    @Column(name = "requested_by", nullable = false, length = 160)
    private String requestedBy;
    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;
    @Column(name = "approved_by", length = 160)
    private String approvedBy;
    @Column(name = "approved_at")
    private LocalDateTime approvedAt;
    @Column(name = "release_requested_by", length = 160)
    private String releaseRequestedBy;
    @Column(name = "release_requested_at")
    private LocalDateTime releaseRequestedAt;
    @Column(name = "released_by", length = 160)
    private String releasedBy;
    @Column(name = "released_at")
    private LocalDateTime releasedAt;
    @Version
    @Column(name = "version", nullable = false)
    private long version;
}
