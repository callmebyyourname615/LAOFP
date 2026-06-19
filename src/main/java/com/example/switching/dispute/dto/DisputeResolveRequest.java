package com.example.switching.dispute.dto;

import jakarta.validation.constraints.NotBlank;

public record DisputeResolveRequest(
        @NotBlank String callingPspId,
        @NotBlank String decision,   // "REFUND" | "NO_ACTION"
        String note
) {}
