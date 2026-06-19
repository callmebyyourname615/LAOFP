package com.example.switching.certification.dto;

import java.time.LocalDateTime;

import com.example.switching.certification.entity.ParticipantCertificationResult;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ParticipantCertificationRecordRequest(
        @NotBlank @Size(max = 32) String bankCode,
        @NotBlank @Size(max = 64) String suiteVersion,
        @NotBlank @Size(min = 40, max = 40) String gitCommit,
        @NotBlank @Size(min = 71, max = 71) String imageDigest,
        @NotBlank @Size(min = 64, max = 64) String evidenceSha256,
        @NotNull ParticipantCertificationResult result,
        @NotNull LocalDateTime executedAt,
        @NotNull LocalDateTime expiresAt,
        @Size(max = 10000) String detailsJson
) {}
