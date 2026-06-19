package com.example.switching.certification.dto;

import java.time.LocalDateTime;

import com.example.switching.certification.entity.ParticipantCertificationEntity;

public record ParticipantCertificationResponse(
        Long id, String certificationRef, String bankCode, String suiteVersion,
        String gitCommit, String imageDigest, String evidenceSha256, String result,
        LocalDateTime executedAt, LocalDateTime expiresAt, String recordedBy, LocalDateTime recordedAt
) {
    public static ParticipantCertificationResponse from(ParticipantCertificationEntity entity) {
        return new ParticipantCertificationResponse(entity.getId(), entity.getCertificationRef(), entity.getBankCode(),
                entity.getSuiteVersion(), entity.getGitCommit(), entity.getImageDigest(), entity.getEvidenceSha256(),
                entity.getResult().name(), entity.getExecutedAt(), entity.getExpiresAt(), entity.getRecordedBy(),
                entity.getRecordedAt());
    }
}
