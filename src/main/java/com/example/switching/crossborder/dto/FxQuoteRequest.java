package com.example.switching.crossborder.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record FxQuoteRequest(
        @NotNull @Positive Long       corridorId,
        @NotNull @Positive BigDecimal amount
) {}
