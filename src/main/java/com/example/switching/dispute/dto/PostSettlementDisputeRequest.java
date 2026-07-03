package com.example.switching.dispute.dto;

import jakarta.validation.constraints.NotBlank;

public record PostSettlementDisputeRequest(
        @NotBlank String transferRef,
        @NotBlank String raisingPspId,
        String reasonCode,
        String reason,
        String evidence
) {}
