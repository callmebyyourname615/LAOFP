package com.example.switching.qr.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.switching.qr.config.QrProperties;
import com.example.switching.qr.dto.QrRefundResponse;
import com.example.switching.qr.exception.QrRefundWindowExpiredException;
import com.example.switching.transfer.exception.TransferNotFoundException;
import com.example.switching.webhook.service.WebhookEventPublisher;

/**
 * Handles QR payment refunds.
 *
 * <p>A refund creates a reversal transaction (SETTLED) from the acquiring PSP
 * back to the issuing PSP, provided the original QR transaction is within the
 * configured refund window (default: 30 days).
 */
@Service
public class QrRefundService {

    private static final Logger log = LoggerFactory.getLogger(QrRefundService.class);

    private final JdbcTemplate          jdbcTemplate;
    private final QrProperties          qrProperties;
    private final WebhookEventPublisher webhookPublisher;

    public QrRefundService(JdbcTemplate jdbcTemplate,
                            QrProperties qrProperties,
                            WebhookEventPublisher webhookPublisher) {
        this.jdbcTemplate     = jdbcTemplate;
        this.qrProperties     = qrProperties;
        this.webhookPublisher = webhookPublisher;
    }

    /**
     * Initiate a refund for a previous QR payment.
     *
     * @param originalTxnId the {@code transaction_ref} of the original QR payment
     * @param amount        amount to refund (may be partial)
     * @return refund record
     * @throws TransferNotFoundException      if the original transaction is not found
     * @throws QrRefundWindowExpiredException if the original transaction is outside the 30-day window
     */
    @Transactional
    public QrRefundResponse refund(String originalTxnId, BigDecimal amount) {

        // 1. Load original transaction
        Map<String, Object> original;
        try {
            original = jdbcTemplate.queryForMap("""
                    SELECT transaction_ref, source_bank, destination_bank,
                           amount, currency, settled_at, created_at
                    FROM transactions
                    WHERE transaction_ref = ?
                      AND status = 'SETTLED'
                    LIMIT 1
                    """, originalTxnId);
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            throw new TransferNotFoundException("Original QR transaction not found: " + originalTxnId);
        }

        // 2. 30-day window check
        // queryForMap returns JDBC types: TIMESTAMP → java.sql.Timestamp, not LocalDateTime
        java.sql.Timestamp rawCreatedAt  = (java.sql.Timestamp) original.get("created_at");
        java.sql.Timestamp rawSettledAt  = (java.sql.Timestamp) original.get("settled_at");
        LocalDateTime createdAt = rawCreatedAt  != null ? rawCreatedAt.toLocalDateTime()
                                : rawSettledAt  != null ? rawSettledAt.toLocalDateTime()
                                : LocalDateTime.now();
        LocalDateTime deadline = createdAt.plusDays(qrProperties.getRefundWindowDays());
        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(deadline)) {
            throw new QrRefundWindowExpiredException(originalTxnId);
        }

        // 3. Create reversal transaction (swap source ↔ destination)
        String refundTxnId = "QRR-" + originalTxnId.replace("QRP-", "").substring(0, 12)
                + "-" + System.nanoTime();
        String srcBank  = (String) original.get("destination_bank");  // acquiring PSP pays back
        String dstBank  = (String) original.get("source_bank");       // issuing PSP receives

        LocalDate today = LocalDate.now();
        jdbcTemplate.update("""
                INSERT INTO transactions (
                    transaction_ref, idempotency_key, flow_ref,
                    source_bank, source_account_no,
                    destination_bank, destination_account_no, destination_account_name,
                    amount, currency, channel_id, route_code, connector_name,
                    status, external_reference, reference,
                    settlement_method, high_value,
                    business_date, accepted_at, settled_at, created_at
                ) VALUES (?, ?, ?, ?, 'QR_REFUND_SRC', ?, 'QR_REFUND_DST', 'QR Refund',
                    ?, ?, 'QR', 'ROUTE_QR_REFUND', 'QR_SERVICE',
                    'SETTLED', ?, ?,
                    'DNS', false,
                    ?, ?, ?, ?)
                """,
                refundTxnId, refundTxnId, "QRR-" + originalTxnId,
                srcBank,
                dstBank,
                amount, (String) original.get("currency"),
                "REFUND-" + originalTxnId, refundTxnId,
                today, now, now, now);

        log.info("QR refund initiated: originalTxnId={} refundTxnId={} amount={}", originalTxnId, refundTxnId, amount);

        // 4. Fire webhook — fire-and-quiet
        Map<String, Object> payload = Map.of(
                "originalTxnId", originalTxnId,
                "refundTxnId",   refundTxnId,
                "amount",        amount.toPlainString(),
                "currency",      (String) original.get("currency"),
                "status",        "COMPLETED");
        webhookPublisher.publishQuietly("QR.REFUND.COMPLETED", dstBank, refundTxnId, payload);
        webhookPublisher.publishQuietly("QR.REFUND.COMPLETED", srcBank, refundTxnId, payload);

        return new QrRefundResponse(refundTxnId, originalTxnId, amount, "COMPLETED", now);
    }
}
