package com.example.switching.participant.dto;

import java.time.LocalDateTime;

public record IssueCertificateResponse(
        String pspId,
        String fileName,
        String certPem,
        String subjectDn,
        String issuerDn,
        String serial,
        LocalDateTime expiresAt) {}
