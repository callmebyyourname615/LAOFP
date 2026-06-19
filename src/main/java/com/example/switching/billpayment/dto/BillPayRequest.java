package com.example.switching.billpayment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record BillPayRequest(
        @NotNull  @Positive Long   tokenId,
        @NotBlank            String payingPspId
) {}
