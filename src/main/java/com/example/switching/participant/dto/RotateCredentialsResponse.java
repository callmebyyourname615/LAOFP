package com.example.switching.participant.dto;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response body for {@code POST /v1/participants/{pspId}/credentials/rotate}.
 *
 * <p>The {@code clientSecret} is returned in plain text exactly once — it is
 * not stored server-side and cannot be recovered after this response is sent.
 */
public record RotateCredentialsResponse(
        @JsonProperty("clientId")      String clientId,
        @JsonProperty("clientSecret")  String clientSecret,
        @JsonProperty("pspId")         String pspId,
        @JsonProperty("expiresAt")     LocalDateTime expiresAt
) {}
