package com.example.switching.rtp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.example.switching.rtp.dto.CreateRtpRequest;
import com.example.switching.rtp.service.RtpRequestFingerprint;
import com.fasterxml.jackson.databind.ObjectMapper;

class RtpRequestFingerprintTest {

    private final RtpRequestFingerprint fingerprint = new RtpRequestFingerprint(new ObjectMapper());

    @Test
    void semanticallyEquivalentPayloadsProduceTheSameFingerprint() {
        CreateRtpRequest first = request("RTP-1", new BigDecimal("1000.00"), "lak", " Invoice 1 ");
        CreateRtpRequest second = request(" RTP-1 ", new BigDecimal("1000.0000"), "LAK", "Invoice 1");

        assertEquals(fingerprint.sha256(first), fingerprint.sha256(second));
    }

    @Test
    void materialPayloadDifferenceChangesFingerprint() {
        assertNotEquals(
                fingerprint.sha256(request("RTP-1", new BigDecimal("1000.00"), "LAK", "Invoice 1")),
                fingerprint.sha256(request("RTP-1", new BigDecimal("1001.00"), "LAK", "Invoice 1")));
    }

    private static CreateRtpRequest request(
            String correlationId,
            BigDecimal amount,
            String currency,
            String description) {
        return new CreateRtpRequest(
                correlationId,
                "BANK_A",
                "BANK_B",
                "010100000001",
                null,
                amount,
                currency,
                description,
                null);
    }
}
