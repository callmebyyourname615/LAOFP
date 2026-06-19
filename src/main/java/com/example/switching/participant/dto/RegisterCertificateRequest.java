package com.example.switching.participant.dto;

/**
 * Request body for {@code POST /v1/participants/{pspId}/certificates/register}.
 *
 * <p>The {@code certPem} field must contain a PEM-encoded X.509 certificate
 * (including the {@code -----BEGIN CERTIFICATE-----} / {@code -----END CERTIFICATE-----}
 * delimiters).  It is typically the DER-encoded cert encoded in Base64 with
 * standard line wrapping.
 */
public record RegisterCertificateRequest(String certPem) {}
