package com.example.switching.rtp.dto;

import java.math.BigDecimal;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ConfirmRtpSettlementRequest(
        @NotBlank String settlementReference,
        @NotNull @DecimalMin(value = "0.0001") BigDecimal settledAmount,
        @Positive Integer installmentNumber) {
}
