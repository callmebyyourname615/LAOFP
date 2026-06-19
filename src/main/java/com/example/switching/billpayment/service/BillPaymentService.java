package com.example.switching.billpayment.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.switching.billpayment.client.BillerApiClient;
import com.example.switching.billpayment.config.BillPaymentProperties;
import com.example.switching.billpayment.dto.BillPayResponse;
import com.example.switching.billpayment.entity.BillerEntity;
import com.example.switching.billpayment.entity.BillTokenEntity;
import com.example.switching.billpayment.exception.BillNotFoundException;
import com.example.switching.billpayment.exception.BillTokenExpiredException;
import com.example.switching.billpayment.exception.BillerTimeoutException;
import com.example.switching.billpayment.exception.DuplicateBillPaymentException;
import com.example.switching.billpayment.repository.BillerRepository;
import com.example.switching.billpayment.repository.BillTokenRepository;
import com.example.switching.liquidity.service.PoolService;
import com.example.switching.webhook.service.WebhookEventPublisher;

/**
 * Orchestrates bill payment: token validation → hold funds → call biller → settle.
 *
 * <p>The method is {@code @Transactional}. If the biller call fails, a
 * {@link BillerTimeoutException} (RuntimeException) is thrown — Spring rolls back
 * the whole transaction, automatically un-doing the pool hold and any DB rows.
 * The PSP may retry with the same {@code tokenId} as long as the token has not
 * expired (within 10 minutes of fetch).
 */
@Service
public class BillPaymentService {

    private static final Logger log = LoggerFactory.getLogger(BillPaymentService.class);

    private final BillerRepository      billerRepo;
    private final BillTokenRepository   tokenRepo;
    private final BillerApiClient       billerApiClient;
    private final BillPaymentProperties props;
    private final PoolService           poolService;
    private final JdbcTemplate          jdbcTemplate;
    private final WebhookEventPublisher webhookPublisher;

    public BillPaymentService(BillerRepository billerRepo,
                              BillTokenRepository tokenRepo,
                              BillerApiClient billerApiClient,
                              BillPaymentProperties props,
                              PoolService poolService,
                              JdbcTemplate jdbcTemplate,
                              WebhookEventPublisher webhookPublisher) {
        this.billerRepo       = billerRepo;
        this.tokenRepo        = tokenRepo;
        this.billerApiClient  = billerApiClient;
        this.props            = props;
        this.poolService      = poolService;
        this.jdbcTemplate     = jdbcTemplate;
        this.webhookPublisher = webhookPublisher;
    }

    /**
     * Pay a bill using a previously fetched token.
     *
     * @param tokenId     ID of the bill token (from {@code GET /v1/bills/fetch})
     * @param payingPspId the PSP whose pool funds the payment
     * @return payment result with receipt number
     * @throws BillNotFoundException       if the token ID does not exist
     * @throws BillTokenExpiredException   if the token is expired or already used (LFP-6002)
     * @throws DuplicateBillPaymentException if a CONFIRMED payment exists for the same billRef within 24h (LFP-6003)
     * @throws BillerTimeoutException      if the biller API fails; triggers full rollback (LFP-6004)
     */
    @Transactional
    public BillPayResponse pay(Long tokenId, String payingPspId) {

        // 1. Load and validate token
        BillTokenEntity token = tokenRepo.findById(tokenId)
                .orElseThrow(() -> new BillNotFoundException("Token " + tokenId));

        LocalDateTime now = LocalDateTime.now();
        if (token.isUsed() || now.isAfter(token.getExpiresAt())) {
            throw new BillTokenExpiredException(tokenId);
        }

        // 2. Load biller
        BillerEntity biller = billerRepo.findById(token.getBillerId())
                .orElseThrow(() -> new BillNotFoundException("Biller " + token.getBillerId()));

        BigDecimal amount  = token.getBillAmount();
        String     billRef = token.getBillRef();

        // 3. 24-hour duplicate check (CONFIRMED payments only)
        Integer dupCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM bill_payments
                WHERE biller_id = ? AND bill_ref = ? AND status = 'CONFIRMED'
                  AND initiated_at > NOW() - INTERVAL '%d hours'
                """.formatted(props.getDuplicateWindowHours()),
                Integer.class, biller.getBillerId(), billRef);
        if (dupCount != null && dupCount > 0) {
            throw new DuplicateBillPaymentException(billRef);
        }

        // 4. Hold funds in paying PSP pool
        String holdRef = "BILL-HOLD-" + tokenId + "-" + System.nanoTime();
        poolService.holdFunds(payingPspId, holdRef, amount);

        // 5. Insert bill_payment row (INITIATED) — gets rolled back if step 6 fails
        Long paymentId = jdbcTemplate.queryForObject(
                """
                INSERT INTO bill_payments
                    (token_id, biller_id, bill_ref, paying_psp_id, amount, status, initiated_at)
                VALUES (?, ?, ?, ?, ?, 'INITIATED', ?)
                RETURNING payment_id
                """,
                Long.class,
                tokenId, biller.getBillerId(), billRef, payingPspId, amount, now);

        // 6. Call biller API — throws BillerTimeoutException on failure → triggers rollback
        String receiptNumber;
        try {
            receiptNumber = billerApiClient.confirmPayment(
                    biller.getApiUrl(), biller.getApiKeyHash(),
                    billRef, amount, payingPspId, biller.getTimeoutSeconds());
        } catch (BillerTimeoutException ex) {
            // Re-throw: @Transactional will roll back hold + INITIATED row
            log.warn("Biller API failed for tokenId={} billRef={}: {}", tokenId, billRef, ex.getMessage());
            throw ex;
        }

        // 7. Biller confirmed — insert SETTLED transaction record
        String txnRef   = "BILL-TXN-" + tokenId + "-" + System.nanoTime();
        LocalDate today = LocalDate.now();
        LocalDateTime confirmedAt = LocalDateTime.now();

        jdbcTemplate.update("""
                INSERT INTO transactions (
                    transaction_ref, idempotency_key, flow_ref,
                    source_bank, source_account_no,
                    destination_bank, destination_account_no, destination_account_name,
                    amount, currency, channel_id, route_code, connector_name,
                    status, external_reference, reference,
                    settlement_method, high_value,
                    business_date, accepted_at, settled_at, created_at
                ) VALUES (?, ?, ?, ?, 'BILL_PAY_SRC', ?, 'BILL_ACQ', ?,
                    ?, 'LAK', 'BILL', 'ROUTE_BILL', 'BILL_SERVICE',
                    'SETTLED', ?, ?,
                    'DNS', false,
                    ?, ?, ?, ?)
                """,
                txnRef, txnRef, "BILL-" + paymentId,
                payingPspId,
                biller.getBillerCode(), biller.getBillerName(),
                amount,
                "BILL-EXT-" + paymentId, txnRef,
                today, confirmedAt, confirmedAt, confirmedAt);

        // 8. Mark token used, update bill_payment to CONFIRMED
        tokenRepo.markUsed(tokenId);
        jdbcTemplate.update("""
                UPDATE bill_payments
                   SET status = 'CONFIRMED', txn_ref = ?, receipt_number = ?, confirmed_at = ?
                 WHERE payment_id = ?
                """, txnRef, receiptNumber, confirmedAt, paymentId);

        // 9. Confirm pool hold
        poolService.confirmHold(holdRef);

        log.info("Bill payment confirmed: tokenId={} paymentId={} txnRef={} receipt={}",
                tokenId, paymentId, txnRef, receiptNumber);

        // 10. Fire webhook — fire-and-quiet
        Map<String, Object> webhookPayload = Map.of(
                "paymentId",     paymentId,
                "txnRef",        txnRef,
                "billerCode",    biller.getBillerCode(),
                "billRef",       billRef,
                "amount",        amount.toPlainString(),
                "receiptNumber", receiptNumber,
                "status",        "CONFIRMED");
        webhookPublisher.publishQuietly("BILL.PAYMENT.CONFIRMED", payingPspId, txnRef, webhookPayload);

        return new BillPayResponse(
                paymentId, txnRef,
                biller.getBillerCode(), billRef,
                amount, receiptNumber, "CONFIRMED", confirmedAt);
    }
}
