package com.example.switching.rtp.service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Component;

import com.example.switching.rtp.dto.CreateRtpRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class RtpRequestFingerprint {

    private final ObjectMapper objectMapper;

    public RtpRequestFingerprint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String sha256(CreateRtpRequest request) {
        Map<String, Object> canonical = new TreeMap<>();
        canonical.put("currency", normalize(request.currency()).toUpperCase());
        canonical.put("description", normalizeNullable(request.description()));
        canonical.put("expiresAt", request.expiresAt() == null ? null : request.expiresAt().toString());
        canonical.put("payeeAccount", normalize(request.payeeAccount()));
        canonical.put("payeeParticipantId", normalize(request.payeeParticipantId()));
        canonical.put("payerAccount", normalizeNullable(request.payerAccount()));
        canonical.put("payerParticipantId", normalize(request.payerParticipantId()));
        canonical.put("requestCorrelationId", normalize(request.requestCorrelationId()));
        canonical.put("requestedAmount", request.requestedAmount().stripTrailingZeros().toPlainString());

        try {
            byte[] json = objectMapper.writeValueAsBytes(canonical);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(json));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to canonicalize RTP request", ex);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
