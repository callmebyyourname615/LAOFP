package com.example.switching.usermgmt.dto;

import java.time.Instant;
import java.util.UUID;
import com.example.switching.usermgmt.enums.MakerCheckerStatus;
import com.fasterxml.jackson.databind.JsonNode;

public record MakerCheckerResponse(
        UUID id,
        String requestType,
        JsonNode payload,
        String payloadSha256,
        String maker,
        String checker,
        MakerCheckerStatus status,
        Instant submittedAt,
        Instant decidedAt,
        String decisionNotes,
        String executionReference) {}
