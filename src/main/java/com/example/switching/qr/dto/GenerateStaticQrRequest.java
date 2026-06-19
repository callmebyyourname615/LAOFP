package com.example.switching.qr.dto;

import jakarta.validation.constraints.NotBlank;

public record GenerateStaticQrRequest(
    @NotBlank String merchantId,
    @NotBlank String pspId,
    String description
) {}
