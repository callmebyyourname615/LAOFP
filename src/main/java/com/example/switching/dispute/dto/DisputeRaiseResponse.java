package com.example.switching.dispute.dto;

import java.time.LocalDateTime;

public record DisputeRaiseResponse(
        Long          disputeId,
        String        txnRef,
        String        status,
        String        disputeType,
        LocalDateTime slaDeadline,
        LocalDateTime raisedAt
) {}
