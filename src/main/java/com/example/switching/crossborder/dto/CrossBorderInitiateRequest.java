package com.example.switching.crossborder.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CrossBorderInitiateRequest(
        @NotNull @Positive Long   quoteId,
        @NotBlank          String initiatingPspId,
        @NotBlank          String beneficiaryName,
        @NotBlank          String beneficiaryBank,
        @NotBlank          String beneficiaryAccount,
        @NotBlank          String beneficiaryCountry,
        String purposeCode,       // required when amount > LAK 5M
        String sourceOfFunds      // required when amount > LAK 5M
) {}
