package com.example.switching.rtp.dto;

import java.math.BigDecimal;
import java.time.Instant;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record RtpInstallmentRequest(
        @Positive int installmentNumber,
        @NotNull Instant dueAt,
        @NotNull @DecimalMin(value = "0.0001") BigDecimal amount) {
}
