package com.example.switching.dispute.dto;

import jakarta.validation.constraints.NotBlank;

public record DrsResolutionSubmitRequest(
        @NotBlank String decision,
        @NotBlank String note,
        String evidence
) {}
