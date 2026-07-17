package com.example.switching.usermgmt.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import com.example.switching.usermgmt.enums.MakerCheckerStatus;

public record MakerCheckerResponse(
        UUID id,
        String requestType,
        Map<String, Object> payload,
        String payloadSha256,
        String maker,
        String checker,
        MakerCheckerStatus status,
        Instant submittedAt,
        Instant decidedAt,
        String decisionNotes,
        String executionReference) {}
