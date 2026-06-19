package com.example.switching.qr.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.switching.qr.dto.QrDecodeResponse;
import com.example.switching.qr.entity.QrCodeEntity;
import com.example.switching.qr.exception.QrChecksumException;
import com.example.switching.qr.exception.QrNotFoundException;
import com.example.switching.qr.repository.QrCodeRepository;

/**
 * Decodes and validates an EMVCo QRCPS-MPM QR payload.
 *
 * <ol>
 *   <li>Verify CRC-16/CCITT checksum.</li>
 *   <li>Parse tag 26/sub-tag 02 to extract the {@code qrId}.</li>
 *   <li>Look up the stored {@link QrCodeEntity}.</li>
 *   <li>Determine validity: not expired, not used (for DYNAMIC).</li>
 * </ol>
 */
@Service
public class QrDecodeService {

    private final QrCodeRepository   qrRepository;
    private final QrGeneratorService generatorService;

    public QrDecodeService(QrCodeRepository qrRepository, QrGeneratorService generatorService) {
        this.qrRepository    = qrRepository;
        this.generatorService = generatorService;
    }

    /**
     * Decode a raw EMVCo payload string, verify its checksum, and return a
     * structured {@link QrDecodeResponse} with validity information.
     *
     * @param qrPayload raw EMVCo QRCPS-MPM string (including "6304" CRC field)
     * @return decoded + enriched response
     * @throws QrChecksumException if CRC-16/CCITT does not match
     * @throws QrNotFoundException if the embedded qrId is not in the database
     */
    @Transactional(readOnly = true)
    public QrDecodeResponse decode(String qrPayload) {
        // 1. Verify checksum
        if (!verifyCrc(qrPayload)) {
            throw new QrChecksumException();
        }

        // 2. Parse all TLV fields from the payload
        Map<String, String> topFields = parseTlv(qrPayload);

        // 3. Extract qrId from MAI sub-tag 26, sub-tag 02
        String maiValue  = topFields.get("26");
        String extractedQrId = (maiValue != null)
                ? parseTlv(maiValue).get("02")
                : null;

        if (extractedQrId == null) {
            throw new QrNotFoundException("<embedded-in-payload>");
        }
        final String qrId = extractedQrId;

        // 4. DB lookup
        QrCodeEntity entity = qrRepository.findByQrId(qrId)
                .orElseThrow(() -> new QrNotFoundException(qrId));

        // 5. Determine validity
        LocalDateTime now = LocalDateTime.now();
        boolean expired = entity.getExpiresAt() != null && now.isAfter(entity.getExpiresAt());
        boolean valid   = !expired && !entity.isUsed();

        String expiryStatus;
        if (expired)          expiryStatus = "EXPIRED";
        else if (entity.isUsed()) expiryStatus = "USED";
        else if (entity.getExpiresAt() == null) expiryStatus = "NO_EXPIRY";
        else                  expiryStatus = "VALID";

        return new QrDecodeResponse(
                entity.getQrId(),
                entity.getMerchantId(),
                entity.getAmount(),
                entity.getCurrency(),
                entity.getQrType(),
                valid,
                expiryStatus,
                entity.getExpiresAt());
    }

    // ── CRC verification ──────────────────────────────────────────────────────

    /**
     * Verify CRC-16/CCITT: the last 4 hex characters are the checksum computed
     * over everything up to and including "6304".
     */
    private boolean verifyCrc(String payload) {
        if (payload == null || payload.length() < 4) {
            return false;
        }
        int idx = payload.lastIndexOf("6304");
        if (idx < 0 || idx + 8 != payload.length()) {
            return false;
        }
        String withoutChecksum = payload.substring(0, idx + 4);
        String embeddedHex     = payload.substring(idx + 4);
        int computed = QrGeneratorService.crc16Ccitt(withoutChecksum);
        try {
            int embedded = Integer.parseInt(embeddedHex, 16);
            return computed == embedded;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    // ── TLV parser ────────────────────────────────────────────────────────────

    /**
     * Parse a flat EMVCo TLV string into a Map of tag→value.
     * Each record is: 2-char tag + 2-char decimal length + value.
     */
    static Map<String, String> parseTlv(String tlvString) {
        Map<String, String> result = new LinkedHashMap<>();
        int pos = 0;
        while (pos + 4 <= tlvString.length()) {
            String tag = tlvString.substring(pos, pos + 2);
            int len;
            try {
                len = Integer.parseInt(tlvString.substring(pos + 2, pos + 4));
            } catch (NumberFormatException e) {
                break;
            }
            if (pos + 4 + len > tlvString.length()) {
                break;
            }
            String value = tlvString.substring(pos + 4, pos + 4 + len);
            result.put(tag, value);
            pos += 4 + len;
        }
        return result;
    }
}
