package com.example.switching.transfer.service;

import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.example.switching.transfer.entity.TransferEntity;

/**
 * Creates and updates rows in the partitioned {@code payment_flows} table.
 *
 * <p>A payment flow represents the full lifecycle of a payment from initiation
 * to final settlement or failure.  It is the single place to ask
 * "what happened to this payment and when?"
 *
 * <p>All methods are "fire-and-quiet" — exceptions are swallowed so that
 * flow tracking never breaks the calling transaction.
 *
 * <p>Lifecycle: {@code INITIATED → DISPATCHED → SETTLED | FAILED}
 */
@Component
public class PaymentFlowTracker {

    private static final Logger log = LoggerFactory.getLogger(PaymentFlowTracker.class);

    private static final String INSERT_SQL = """
            INSERT INTO payment_flows
                (flow_ref, inquiry_ref, transaction_ref, source_bank, destination_bank,
                 channel_id, amount, currency, status, business_date)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'INITIATED', ?)
            ON CONFLICT DO NOTHING
            """;

    private static final String UPDATE_DISPATCHED_SQL = """
            UPDATE payment_flows
            SET status = 'DISPATCHED', updated_at = NOW()
            WHERE transaction_ref = ? AND business_date = ?
            """;

    private static final String UPDATE_SETTLED_SQL = """
            UPDATE payment_flows
            SET status = 'SETTLED', settled_at = NOW(), updated_at = NOW()
            WHERE transaction_ref = ? AND business_date = ?
            """;

    private static final String UPDATE_FAILED_SQL = """
            UPDATE payment_flows
            SET status = 'FAILED', failed_at = NOW(), updated_at = NOW()
            WHERE transaction_ref = ? AND business_date = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public PaymentFlowTracker(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Create the initial flow record when a transfer is accepted.
     * Uses {@code ON CONFLICT DO NOTHING} so re-runs are safe.
     */
    public void initFlow(TransferEntity transfer) {
        try {
            jdbcTemplate.update(INSERT_SQL,
                    transfer.getFlowRef(),
                    transfer.getInquiryRef(),
                    transfer.getTransferRef(),
                    transfer.getSourceBank(),
                    transfer.getDestinationBank(),
                    transfer.getChannelId() != null ? transfer.getChannelId() : "API",
                    transfer.getAmount(),
                    transfer.getCurrency() != null ? transfer.getCurrency() : "LAK",
                    transfer.getBusinessDate());
        } catch (Exception ex) {
            log.warn("Failed to init payment flow for transferRef={} — {}",
                    transfer.getTransferRef(), ex.getMessage());
        }
    }

    /** Mark the flow as DISPATCHED (outbox worker picked it up and sent to bank). */
    public void markDispatched(String transactionRef, LocalDate businessDate) {
        quietly(() -> jdbcTemplate.update(UPDATE_DISPATCHED_SQL, transactionRef, businessDate),
                "DISPATCHED", transactionRef);
    }

    /** Mark the flow as SETTLED (bank confirmed). */
    public void markSettled(String transactionRef, LocalDate businessDate) {
        quietly(() -> jdbcTemplate.update(UPDATE_SETTLED_SQL, transactionRef, businessDate),
                "SETTLED", transactionRef);
    }

    /** Mark the flow as FAILED (terminal rejection, no more retries). */
    public void markFailed(String transactionRef, LocalDate businessDate) {
        quietly(() -> jdbcTemplate.update(UPDATE_FAILED_SQL, transactionRef, businessDate),
                "FAILED", transactionRef);
    }

    private void quietly(Runnable action, String status, String transactionRef) {
        try {
            action.run();
        } catch (Exception ex) {
            log.warn("Failed to update payment_flows to {} for transferRef={} — {}",
                    status, transactionRef, ex.getMessage());
        }
    }
}
