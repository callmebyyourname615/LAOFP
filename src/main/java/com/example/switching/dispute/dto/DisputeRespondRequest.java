package com.example.switching.dispute.dto;

import jakarta.validation.constraints.NotBlank;

public record DisputeRespondRequest(
        @NotBlank String callingPspId,
        String evidence   // JSON array string with new evidence items
) {}
