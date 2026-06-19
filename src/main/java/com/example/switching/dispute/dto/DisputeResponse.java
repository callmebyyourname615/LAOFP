package com.example.switching.dispute.dto;

import java.time.LocalDateTime;

public record DisputeResponse(
        Long          disputeId,
        String        txnRef,
        String        raisingPspId,
        String        respondingPspId,
        String        disputeType,
        String        status,
        LocalDateTime raisedAt,
        LocalDateTime slaDeadline,
        LocalDateTime resolvedAt,
        String        evidence,
        String        resolutionNote,
        boolean       autoRuled
) {}
