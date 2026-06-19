package com.example.switching.qr.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record GenerateDynamicQrRequest(
    @NotBlank String merchantId,
    @NotBlank String pspId,
    @NotNull @DecimalMin("0.01") BigDecimal amount,
    String currency,
    String txnRef,
    int expiresInSeconds
) {}
