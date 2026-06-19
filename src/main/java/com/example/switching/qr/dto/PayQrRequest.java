package com.example.switching.qr.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;

public record PayQrRequest(
    @NotBlank String qrId,
    @NotBlank String issuingPspId,
    /** Required for STATIC QR; ignored for DYNAMIC QR (amount is embedded). */
    BigDecimal amount
) {}
