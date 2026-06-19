package com.example.switching.operations.dto;

public record OperationsOutboxMarkReviewedRequest(
        String reason,
        String reviewedBy
) {
}