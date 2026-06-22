package com.example.switching.rtp.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import com.example.switching.rtp.enums.RtpAuthorisationMode;
import com.example.switching.rtp.enums.RtpStatus;

public record RtpRequestResponse(
        UUID id, String requestCorrelationId, String payeeParticipantId, String payerParticipantId,
        String payeeAccount, String payerAccount, BigDecimal requestedAmount, BigDecimal authorisedAmount,
        BigDecimal settledAmount, String currency, String description, RtpStatus status,
        RtpAuthorisationMode authorisationMode, Instant expiresAt, String transferReference,
        String settlementReference, String declineReason, Instant declinedAt, Instant authorisedAt, Instant settledAt,
        String cancellationReason, Instant cancelledAt, Instant createdAt, long version) {}
