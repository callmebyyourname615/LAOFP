package com.example.switching.compliance.legalhold.dto;

import java.time.LocalDate;

import com.example.switching.compliance.legalhold.entity.LegalHoldScopeType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record LegalHoldRequest(
        @NotNull LegalHoldScopeType scopeType,
        @NotBlank @Size(max = 160) String scopeKey,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        @NotBlank @Size(max = 1000) String reason,
        @NotBlank @Size(max = 160) String caseReference
) {}
