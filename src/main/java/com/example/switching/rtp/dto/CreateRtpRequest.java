package com.example.switching.rtp.dto;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateRtpRequest(
        @NotBlank @Size(max = 64) @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._:-]{0,63}$") String requestCorrelationId,
        @NotBlank @Size(max = 64) String payeeParticipantId,
        @NotBlank @Size(max = 64) String payerParticipantId,
        @NotBlank @Size(max = 128) String payeeAccount,
        @Size(max = 128) String payerAccount,
        @NotNull @Positive @Digits(integer = 15, fraction = 4) BigDecimal requestedAmount,
        @NotBlank @Pattern(regexp = "^[A-Za-z]{3}$") String currency,
        @Size(max = 280) String description,
        @Future Instant expiresAt) {
}
