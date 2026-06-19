package com.example.switching.qr.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.switching.qr.config.QrProperties;
import com.example.switching.qr.entity.QrCodeEntity;
import com.example.switching.qr.exception.DuplicateTxnRefException;
import com.example.switching.qr.repository.QrCodeRepository;

/**
 * Generates ISO 20022 / EMVCo QRCPS-MPM (Merchant Presented Mode) QR code payloads.
 *
 * <h3>Payload structure (Tag-Length-Value)</h3>
 * <pre>
 *  ID  Description
 *  00  Payload Format Indicator  → "01"
 *  01  Point of Initiation       → "11" (static) | "12" (dynamic)
 *  26  Merchant Account Info     → sub-tag 00=AID ("com.laofp"), 01=merchant ID
 *  52  Merchant Category Code    → "0000" (generic)
 *  53  Transaction Currency      → "418" (LAK)
 *  54  Transaction Amount        → DYNAMIC only
 *  58  Country Code              → "LA"
 *  59  Merchant Name             → merchantId (truncated to 25 chars)
 *  60  Merchant City             → "VIENTIANE"
 *  62  Additional Data           → sub-tag 05=txnRef (DYNAMIC)
 *  63  CRC-16/CCITT              → 4-hex-digit checksum over full string + "6304"
 * </pre>
 *
 * CRC polynomial: 0x1021, initial value: 0xFFFF (no inversion on in or out).
 */
@Service
public class QrGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(QrGeneratorService.class);

    /** ISO 4217 numeric code for Lao Kip. */
    private static final String LAK_CODE = "418";

    /** LaoFP application ID for MAI sub-tag 00. */
    private static final String APP_ID = "com.laofp";

    private final QrCodeRepository qrRepository;
    private final QrProperties     qrProperties;

    public QrGeneratorService(QrCodeRepository qrRepository, QrProperties qrProperties) {
        this.qrRepository  = qrRepository;
        this.qrProperties  = qrProperties;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Generate a STATIC QR code for a merchant.
     * Static codes are reusable (no expiry, no amount, no single-use flag).
     *
     * @param merchantId  merchant identifier
     * @param pspId       acquiring PSP
     * @param description optional description (stored internally, not in payload)
     * @return persisted {@link QrCodeEntity}
     */
    @Transactional
    public QrCodeEntity generateStatic(String merchantId, String pspId, String description) {
        String qrId   = UUID.randomUUID().toString();
        String payload = buildPayload(qrId, merchantId, "STATIC", null, null, "LAK");

        QrCodeEntity entity = new QrCodeEntity();
        entity.setQrId(qrId);
        entity.setMerchantId(merchantId);
        entity.setPspId(pspId);
        entity.setQrType("STATIC");
        entity.setPayloadText(payload);
        entity.setCurrency("LAK");
        // No amount, no txnRef, no expiry

        QrCodeEntity saved = qrRepository.save(entity);
        log.info("Static QR generated: qrId={} merchantId={} pspId={}", qrId, merchantId, pspId);
        return saved;
    }

    /**
     * Generate a DYNAMIC QR code for a specific payment.
     * Dynamic codes are single-use and carry a fixed amount + optional expiry.
     *
     * @param merchantId       merchant identifier
     * @param pspId            acquiring PSP
     * @param amount           exact payment amount
     * @param txnRef           caller-supplied transaction reference (must be unique)
     * @param expiresInSeconds seconds until expiry; 0 = use default max
     * @return persisted {@link QrCodeEntity}
     * @throws DuplicateTxnRefException if {@code txnRef} already exists
     */
    @Transactional
    public QrCodeEntity generateDynamic(String merchantId,
                                         String pspId,
                                         BigDecimal amount,
                                         String txnRef,
                                         int expiresInSeconds) {
        if (txnRef != null && qrRepository.existsByTxnRef(txnRef)) {
            throw new DuplicateTxnRefException(txnRef);
        }

        String qrId  = UUID.randomUUID().toString();
        int clampedSec = (expiresInSeconds <= 0)
                ? qrProperties.getDynamicMaxExpiryMinutes() * 60
                : Math.min(expiresInSeconds, qrProperties.getDynamicMaxExpiryMinutes() * 60);
        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(clampedSec);

        String payload = buildPayload(qrId, merchantId, "DYNAMIC", amount, txnRef, "LAK");

        QrCodeEntity entity = new QrCodeEntity();
        entity.setQrId(qrId);
        entity.setMerchantId(merchantId);
        entity.setPspId(pspId);
        entity.setQrType("DYNAMIC");
        entity.setPayloadText(payload);
        entity.setAmount(amount);
        entity.setCurrency("LAK");
        entity.setTxnRef(txnRef);
        entity.setExpiresAt(expiresAt);

        QrCodeEntity saved = qrRepository.save(entity);
        log.info("Dynamic QR generated: qrId={} merchantId={} amount={} txnRef={} expiresAt={}",
                qrId, merchantId, amount, txnRef, expiresAt);
        return saved;
    }

    // ── EMVCo payload builder ─────────────────────────────────────────────────

    /**
     * Builds the EMVCo QRCPS-MPM payload string with CRC-16/CCITT appended.
     */
    public String buildPayload(String qrId, String merchantId,
                                String qrType,
                                BigDecimal amount, String txnRef,
                                String currency) {
        StringBuilder sb = new StringBuilder();

        // 00: Payload Format Indicator
        tlv(sb, "00", "01");

        // 01: Point of Initiation Method
        tlv(sb, "01", "STATIC".equals(qrType) ? "11" : "12");

        // 26: Merchant Account Information
        StringBuilder mai = new StringBuilder();
        tlv(mai, "00", APP_ID);
        tlv(mai, "01", truncate(merchantId, 32));
        tlv(mai, "02", qrId);
        tlv(sb, "26", mai.toString());

        // 52: Merchant Category Code (generic)
        tlv(sb, "52", "0000");

        // 53: Transaction Currency (LAK = 418)
        String currCode = "LAK".equalsIgnoreCase(currency) ? LAK_CODE : LAK_CODE;
        tlv(sb, "53", currCode);

        // 54: Transaction Amount (DYNAMIC only)
        if (amount != null) {
            tlv(sb, "54", amount.toPlainString());
        }

        // 58: Country Code
        tlv(sb, "58", "LA");

        // 59: Merchant Name (≤25 chars per EMVCo spec)
        tlv(sb, "59", truncate(merchantId, 25));

        // 60: Merchant City
        tlv(sb, "60", "VIENTIANE");

        // 62: Additional Data Field
        if (txnRef != null && !txnRef.isBlank()) {
            StringBuilder adf = new StringBuilder();
            tlv(adf, "05", truncate(txnRef, 25));
            tlv(sb, "62", adf.toString());
        }

        // 63: CRC placeholder — compute over sb + "6304"
        sb.append("6304");
        int crc = crc16Ccitt(sb.toString());
        sb.append(String.format("%04X", crc));

        return sb.toString();
    }

    // ── CRC-16/CCITT ─────────────────────────────────────────────────────────

    /**
     * CRC-16/CCITT (polynomial 0x1021, init 0xFFFF, no input/output inversion).
     * Computes over the UTF-8 byte representation of the input string.
     */
    public static int crc16Ccitt(String data) {
        int crc = 0xFFFF;
        for (byte b : data.getBytes(java.nio.charset.StandardCharsets.UTF_8)) {
            int unsignedByte = b & 0xFF;
            for (int i = 0; i < 8; i++) {
                boolean xorFlag = ((crc ^ (unsignedByte << 8)) & 0x8000) != 0;
                crc = (crc << 1) & 0xFFFF;
                if (xorFlag) {
                    crc ^= 0x1021;
                }
                unsignedByte = (unsignedByte << 1) & 0xFF;
            }
        }
        return crc;
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /**
     * Appends a TLV field: {@code tag + length(2 digits) + value} to {@code sb}.
     */
    private static void tlv(StringBuilder sb, String tag, String value) {
        String len = String.format("%02d", value.length());
        sb.append(tag).append(len).append(value);
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }
}
