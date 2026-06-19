package com.example.switching.security.breakglass.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PrivilegedAccessRequest(
        @NotBlank @Size(max = 1000) String reason,
        @NotBlank @Size(max = 160) String ticketReference,
        @Min(1) @Max(30) int ttlMinutes,
        @Min(1) @Max(20) int maxUses
) {}
