package com.example.switching.qr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.example.switching.AbstractIntegrationTest;
import com.example.switching.qr.entity.QrCodeEntity;
import com.example.switching.qr.exception.DuplicateTxnRefException;
import com.example.switching.qr.service.QrDecodeService;
import com.example.switching.qr.service.QrGeneratorService;

/**
 * Integration tests for QR code generation (TC-QR-GEN-001 – 005).
 *
 * Tests:
 * - STATIC QR generated with correct fields and valid CRC-16/CCITT
 * - DYNAMIC QR generated with amount, txnRef, expiresAt
 * - DYNAMIC QR with duplicate txnRef throws LFP-QR-003
 * - CRC-16/CCITT verification works for generated payloads
 * - Decoded payload extracts correct merchant ID and QR type
 */
class QrGenerationIntegrationTest extends AbstractIntegrationTest {

    @Autowired private QrGeneratorService generatorService;
    @Autowired private QrDecodeService    decodeService;

    // ── TC-QR-GEN-001 ────────────────────────────────────────────────────────

    @Test
    void generateStatic_createsQrWithValidPayloadAndCrc() {
        QrCodeEntity qr = generatorService.generateStatic("MERCHANT_A", "BANK_A", "Test static QR");

        assertNotNull(qr.getQrId(),        "qrId must be generated");
        assertEquals("STATIC", qr.getQrType());
        assertEquals("BANK_A", qr.getPspId());
        assertEquals("MERCHANT_A", qr.getMerchantId());
        assertNull(qr.getAmount(),         "STATIC QR must have no amount");
        assertNull(qr.getExpiresAt(),      "STATIC QR must have no expiry");
        assertNull(qr.getTxnRef(),         "STATIC QR must have no txnRef");

        String payload = qr.getPayloadText();
        assertNotNull(payload);
        assertTrue(payload.startsWith("000201"),      "Payload must start with Format Indicator 01");
        assertTrue(payload.contains("010211"),        "STATIC QR must have initiation method 11");
        assertTrue(payload.contains("6304"),          "Payload must include CRC field");
        assertTrue(payload.length() > 10,             "Payload must be non-trivial");
    }

    // ── TC-QR-GEN-002 ────────────────────────────────────────────────────────

    @Test
    void generateDynamic_createsQrWithAmountAndExpiry() {
        String txnRef = "TXN-DYN-" + System.nanoTime();
        BigDecimal amount = new BigDecimal("12500.00");

        QrCodeEntity qr = generatorService.generateDynamic(
                "MERCHANT_B", "BANK_B", amount, txnRef, 300);

        assertNotNull(qr.getQrId());
        assertEquals("DYNAMIC", qr.getQrType());
        assertEquals("BANK_B", qr.getPspId());
        assertEquals(0, amount.compareTo(qr.getAmount()), "Amount must match");
        assertEquals(txnRef, qr.getTxnRef());
        assertNotNull(qr.getExpiresAt(),  "DYNAMIC QR must have expiry");
        assertTrue(qr.getPayloadText().contains("010212"), "DYNAMIC initiation method must be 12");
    }

    // ── TC-QR-GEN-003 ────────────────────────────────────────────────────────

    @Test
    void generateDynamic_duplicateTxnRef_throwsLfpQr003() {
        String txnRef = "TXN-DUP-" + System.nanoTime();
        generatorService.generateDynamic("MERCHANT_C", "BANK_A",
                new BigDecimal("100.00"), txnRef, 300);

        // Second call with same txnRef must throw LFP-QR-003
        assertThrows(DuplicateTxnRefException.class,
                () -> generatorService.generateDynamic("MERCHANT_C", "BANK_A",
                        new BigDecimal("200.00"), txnRef, 300),
                "Duplicate txnRef must throw DuplicateTxnRefException");
    }

    // ── TC-QR-GEN-004 ────────────────────────────────────────────────────────

    @Test
    void crc16_verifiedCorrectlyOnGeneratedPayload() {
        QrCodeEntity qr = generatorService.generateStatic("MERCHANT_D", "BANK_A", null);
        String payload = qr.getPayloadText();

        // The last 4 chars of the payload are the CRC hex
        assertTrue(payload.length() >= 8, "Payload must have at least 4 chars for CRC");
        String withoutCrc    = payload.substring(0, payload.length() - 4);
        String embeddedCrcHex = payload.substring(payload.length() - 4);

        int computed = QrGeneratorService.crc16Ccitt(withoutCrc);
        int embedded = Integer.parseInt(embeddedCrcHex, 16);
        assertEquals(computed, embedded, "CRC-16/CCITT must match the embedded checksum");
    }

    // ── TC-QR-GEN-005 ────────────────────────────────────────────────────────

    @Test
    void decode_staticQr_extractsMerchantAndType() {
        QrCodeEntity qr = generatorService.generateStatic("MERCHANT_E", "BANK_B", null);

        var decoded = decodeService.decode(qr.getPayloadText());

        assertEquals(qr.getQrId(),      decoded.qrId());
        assertEquals("MERCHANT_E",      decoded.merchantId());
        assertEquals("STATIC",          decoded.qrType());
        assertTrue(decoded.valid(),     "STATIC QR with no expiry must be valid");
        assertEquals("NO_EXPIRY",       decoded.expiryStatus());
    }
}
