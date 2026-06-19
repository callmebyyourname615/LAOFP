package com.example.switching.certification.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "participant_certifications")
@Getter
@Setter
public class ParticipantCertificationEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "certification_ref", nullable = false, unique = true, length = 64)
    private String certificationRef;
    @Column(name = "bank_code", nullable = false, length = 32)
    private String bankCode;
    @Column(name = "suite_version", nullable = false, length = 64)
    private String suiteVersion;
    @Column(name = "git_commit", nullable = false, length = 40)
    private String gitCommit;
    @Column(name = "image_digest", nullable = false, length = 71)
    private String imageDigest;
    @Column(name = "evidence_sha256", nullable = false, unique = true, length = 64)
    private String evidenceSha256;
    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false, length = 16)
    private ParticipantCertificationResult result;
    @Column(name = "executed_at", nullable = false)
    private LocalDateTime executedAt;
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;
    @Column(name = "recorded_by", nullable = false, length = 160)
    private String recordedBy;
    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;
    @Column(name = "details_json", columnDefinition = "TEXT")
    private String detailsJson;
}
