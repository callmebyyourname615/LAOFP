package com.example.switching.settlement.dto;

import java.time.LocalDateTime;

public record SettlementReportArtifactResponse(
        Long id,
        String reportRef,
        String pspId,
        String reportType,
        LocalDateTime generatedAt
) {}
