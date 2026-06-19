package com.example.switching.qr.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.switching.liquidity.service.PoolService;
import com.example.switching.qr.dto.QrPayResponse;
import com.example.switching.qr.entity.QrCodeEntity;
import com.example.switching.qr.exception.QrAlreadyUsedException;
import com.example.switching.qr.exception.QrExpiredException;
import com.example.switching.qr.exception.QrNotFoundException;
import com.example.switching.qr.repository.QrCodeRepository;
import com.example.switching.webhook.service.WebhookEventPublisher;

/**
 * Executes a QR payment end-to-end:
 *
 * <ol>
 *   <li>Load + validate QR (not expired, not used for DYNAMIC).</li>
 *   <li>{@link PoolService#holdFunds} on the issuing PSP.</li>
 *   <li>Insert a SETTLED transaction record (real-time direct credit — no outbox).</li>
 *   <li>Mark the QR as used (DYNAMIC only).</li>
 *   <li>{@link PoolService#confirmHold} on the issuing PSP.</li>
 *   <li>Fire {@code QR.PAYMENT.COMPLETED} webhook to both issuing + acquiring PSPs.</li>
 * </ol>
 *
 * <p>The entire sequence runs in a single transaction so that a pool hold is never
 * left dangling on commit failure.
 */
@Service
public class QrPaymentService {

    private static final Logger log = LoggerFactory.getLogger(QrPaymentService.class);

    private final QrCodeRepository     qrRepository;
    private final PoolService          poolService;
    private final JdbcTemplate         jdbcTemplate;
    private final WebhookEventPublisher webhookPublisher;

    public QrPaymentService(QrCodeRepository qrRepository,
                             PoolService poolService,
                             JdbcTemplate jdbcTemplate,
                             WebhookEventPublisher webhookPublisher) {
        this.qrRepository     = qrRepository;
        this.poolService      = poolService;
        this.jdbcTemplate     = jdbcTemplate;
        this.webhookPublisher = webhookPublisher;
    }

    /**
     * Execute a QR payment.
     *
     * @param qrId          the QR code identifier
     * @param issuingPspId  PSP of the paying customer
     * @param overrideAmount amount to charge (required for STATIC QR; ignored for DYNAMIC)
     * @return payment result
     * @throws QrNotFoundException    if the QR code is not found
     * @throws QrExpiredException     if the QR code has expired
     * @throws QrAlreadyUsedException if a DYNAMIC QR has already been paid
     */
    @Transactional
    public QrPayResponse pay(String qrId, String issuingPspId, BigDecimal overrideAmount) {

        // 1. Load + validate QR
        QrCodeEntity qr = qrRepository.findByQrId(qrId)
                .orElseThrow(() -> new QrNotFoundException(qrId));

        LocalDateTime now = LocalDateTime.now();

        if (qr.getExpiresAt() != null && now.isAfter(qr.getExpiresAt())) {
            throw new QrExpiredException(qrId);
        }
        if (qr.isUsed()) {
            throw new QrAlreadyUsedException(qrId);
        }

        // 2. Resolve amount
        BigDecimal amount = "DYNAMIC".equals(qr.getQrType())
                ? qr.getAmount()
                : overrideAmount;
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Payment amount must be provided and positive for STATIC QR codes");
        }

        String acquiringPspId = qr.getPspId();
        String txnRef = "QRP-" + qrId.replace("-", "").substring(0, 12).toUpperCase()
                + "-" + System.nanoTime();

        // 3. Hold funds from issuing PSP pool
        poolService.holdFunds(issuingPspId, txnRef, amount);

        // 4. Insert SETTLED transaction record (real-time; bypasses outbox/FPRE)
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
                ) VALUES (?, ?, ?, ?, 'QR_PAY', ?, 'QR_ACQ', ?,
                    ?, ?, 'QR', 'ROUTE_QR', 'QR_SERVICE',
                    'SETTLED', ?, ?,
                    'DNS', false,
                    ?, ?, ?, ?)
                """,
                txnRef, txnRef, "QR-" + qrId,
                issuingPspId,
                acquiringPspId, qr.getMerchantId(),
                amount, qr.getCurrency(),
                "QR-" + qrId, txnRef,
                today, now, now, now);

        // 5. Mark QR used (DYNAMIC only — STATIC can be re-scanned)
        if ("DYNAMIC".equals(qr.getQrType())) {
            qrRepository.markUsed(qrId);
        }

        // 6. Confirm pool hold (debit finalised)
        poolService.confirmHold(txnRef);

        log.info("QR payment completed: qrId={} issuingPsp={} acquiringPsp={} amount={} txnRef={}",
                qrId, issuingPspId, acquiringPspId, amount, txnRef);

        // 7. Fire webhook — fire-and-quiet; must not block the commit
        Map<String, Object> payload = Map.of(
                "qrId",         qrId,
                "txnRef",       txnRef,
                "issuingPspId", issuingPspId,
                "acquiringPspId", acquiringPspId,
                "amount",       amount.toPlainString(),
                "currency",     qr.getCurrency(),
                "status",       "COMPLETED");
        webhookPublisher.publishQuietly("QR.PAYMENT.COMPLETED", issuingPspId,  txnRef, payload);
        webhookPublisher.publishQuietly("QR.PAYMENT.COMPLETED", acquiringPspId, txnRef, payload);

        return new QrPayResponse(
                txnRef, qrId, issuingPspId, acquiringPspId,
                amount, qr.getCurrency(), "COMPLETED", now);
    }
}
