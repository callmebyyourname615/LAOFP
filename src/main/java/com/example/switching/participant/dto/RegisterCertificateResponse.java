package com.example.switching.participant.dto;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response body for {@code POST /v1/participants/{pspId}/certificates/register}.
 */
public record RegisterCertificateResponse(
        @JsonProperty("certId")      String certId,
        @JsonProperty("pspId")       String pspId,
        @JsonProperty("fingerprint") String fingerprint,
        @JsonProperty("subjectDn")   String subjectDn,
        @JsonProperty("expiresAt")   LocalDateTime expiresAt
) {}
