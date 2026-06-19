package com.example.switching.configchange.dto;

import com.example.switching.configchange.entity.ConfigurationTargetType;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ConfigurationChangeCreateRequest(
        @NotNull ConfigurationTargetType targetType,
        @NotBlank @Size(max = 160) String targetKey,
        @NotBlank @Size(max = 512) String desiredValue,
        @NotBlank @Size(max = 1000) String reason,
        @NotBlank @Size(max = 160) String ticketReference,
        @Min(1) @Max(24) int validHours
) {}
