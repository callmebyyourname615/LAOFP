package com.example.switching.participant.dto;

import java.time.LocalDateTime;

public record ParticipantCertificateInventoryResponse(
        String certId,
        String pspId,
        String pspName,
        String fingerprint,
        String subjectDn,
        LocalDateTime issuedAt,
        LocalDateTime expiresAt,
        String status,
        LocalDateTime createdAt) {}
