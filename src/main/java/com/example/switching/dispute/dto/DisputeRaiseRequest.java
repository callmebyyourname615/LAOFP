package com.example.switching.dispute.dto;

import jakarta.validation.constraints.NotBlank;

public record DisputeRaiseRequest(
        @NotBlank String txnRef,
        @NotBlank String disputeType,
        @NotBlank String raisingPspId,
        String evidence,     // JSON array string, e.g. "[\"photo1.jpg\"]"
        String description
) {}
