package com.example.switching.dispute.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.switching.dispute.entity.DisputeEntity;
import com.example.switching.dispute.exception.DisputeNotFoundException;
import com.example.switching.dispute.repository.DisputeRepository;
import com.example.switching.liquidity.service.PoolService;
import com.example.switching.webhook.service.WebhookEventPublisher;

/**
 * Executes the financial leg of a dispute refund:
 * hold funds from the responding PSP → insert SETTLED reversal transaction
 * → update {@code refund_transactions} to COMPLETED → confirm hold → fire webhook.
 *
 * <p>This is called from {@link DisputeResolutionService} (manual RESOLVED_REFUND)
 * and from {@link DisputeSlaEnforcementService} (SLA auto-rule).
 */
@Service
public class DisputeAutoRefundService {

    private static final Logger log = LoggerFactory.getLogger(DisputeAutoRefundService.class);

    private final DisputeRepository     disputeRepo;
    private final JdbcTemplate          jdbcTemplate;
    private final PoolService           poolService;
    private final WebhookEventPublisher webhookPublisher;

    public DisputeAutoRefundService(DisputeRepository disputeRepo,
                                    JdbcTemplate jdbcTemplate,
                                    PoolService poolService,
                                    WebhookEventPublisher webhookPublisher) {
        this.disputeRepo      = disputeRepo;
        this.jdbcTemplate     = jdbcTemplate;
        this.poolService      = poolService;
        this.webhookPublisher = webhookPublisher;
    }

    /**
     * Initiate the financial refund for a dispute.  The dispute must already be in
     * a terminal status ({@code RESOLVED_REFUND}) before this is called.
     *
     * @param disputeId ID of the resolved dispute
     */
    @Transactional
    public void initiateRefund(Long disputeId) {
        DisputeEntity dispute = disputeRepo.findById(disputeId)
                .orElseThrow(() -> new DisputeNotFoundException(disputeId));

        // Load original transaction for amount + PSP roles
        Map<String, Object> txn;
        try {
            txn = jdbcTemplate.queryForMap(
                    "SELECT amount, source_bank, destination_bank FROM transactions WHERE transaction_ref = ? AND status = 'SETTLED' LIMIT 1",
                    dispute.getTxnRef());
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            log.warn("Original transaction missing for dispute {}: {}", disputeId, dispute.getTxnRef());
            throw new IllegalStateException("Original transaction not found: " + dispute.getTxnRef());
        }

        BigDecimal amount          = new BigDecimal(txn.get("amount").toString());
        String     respondingPspId = dispute.getRespondingPspId();  // pays refund
        String     raisingPspId   = dispute.getRaisingPspId();      // receives refund

        LocalDateTime now   = LocalDateTime.now();
        LocalDate     today = LocalDate.now();

        // 1. Insert refund_transactions INITIATED
        String refundRef = "DISP-HOLD-" + disputeId + "-" + System.nanoTime();
        Long refundId = jdbcTemplate.queryForObject(
                """
                INSERT INTO refund_transactions
                    (dispute_id, original_txn_ref, amount, status, initiated_at)
                VALUES (?, ?, ?, 'INITIATED', ?)
                RETURNING refund_id
                """,
                Long.class, disputeId, dispute.getTxnRef(), amount, now);

        // 2. Hold from responding PSP pool
        poolService.holdFunds(respondingPspId, refundRef, amount);

        // 3. Insert SETTLED reversal transaction (responding → raising)
        String refundTxnRef = "DISP-TXN-" + disputeId + "-" + System.nanoTime();
        jdbcTemplate.update("""
                INSERT INTO transactions (
                    transaction_ref, idempotency_key, flow_ref,
                    source_bank, source_account_no,
                    destination_bank, destination_account_no, destination_account_name,
                    amount, currency, channel_id, route_code, connector_name,
                    status, external_reference, reference,
                    settlement_method, high_value,
                    business_date, accepted_at, settled_at, created_at
                ) VALUES (?, ?, ?, ?, 'DISPUTE_SRC', ?, 'DISPUTE_DST', 'Dispute Refund',
                    ?, 'LAK', 'DISPUTE', 'ROUTE_DISPUTE', 'DISPUTE_SERVICE',
                    'SETTLED', ?, ?,
                    'DNS', false,
                    ?, ?, ?, ?)
                """,
                refundTxnRef, refundTxnRef, "DISP-" + disputeId,
                respondingPspId,
                raisingPspId,
                amount,
                "DISP-" + disputeId, refundTxnRef,
                today, now, now, now);

        // 4. Update refund_transactions COMPLETED
        jdbcTemplate.update(
                "UPDATE refund_transactions SET status='COMPLETED', refund_txn_ref=?, completed_at=? WHERE refund_id=?",
                refundTxnRef, now, refundId);

        // 5. Confirm pool hold
        poolService.confirmHold(refundRef);

        log.info("Dispute refund completed: disputeId={} refundTxnRef={} amount={}", disputeId, refundTxnRef, amount);

        // 6. Fire webhook to both PSPs
        Map<String, Object> payload = Map.of(
                "disputeId",    disputeId,
                "refundTxnRef", refundTxnRef,
                "amount",       amount.toPlainString(),
                "status",       "COMPLETED");
        webhookPublisher.publishQuietly("DISPUTE.REFUND.COMPLETED", raisingPspId,    refundTxnRef, payload);
        webhookPublisher.publishQuietly("DISPUTE.REFUND.COMPLETED", respondingPspId, refundTxnRef, payload);
    }
}
