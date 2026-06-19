package com.example.switching.dispute.service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.switching.dispute.config.DisputeProperties;
import com.example.switching.dispute.dto.DisputeRaiseRequest;
import com.example.switching.dispute.dto.DisputeRaiseResponse;
import com.example.switching.dispute.exception.DisputeAlreadyExistsException;
import com.example.switching.dispute.exception.DisputeNotFoundException;
import com.example.switching.dispute.exception.DisputeTypeInvalidException;
import com.example.switching.dispute.exception.DisputeWindowExpiredException;
import com.example.switching.webhook.service.WebhookEventPublisher;

/**
 * Raises a new dispute against an original transaction.
 *
 * <p>Validation sequence:
 * <ol>
 *   <li>Validate dispute type</li>
 *   <li>Load original transaction → check 90-day window</li>
 *   <li>Check no active dispute for the same txnRef</li>
 *   <li>Compute SLA deadline by type</li>
 *   <li>Persist dispute row</li>
 *   <li>Fire DISPUTE.STATUS_CHANGED webhook to both PSPs</li>
 * </ol>
 */
@Service
public class DisputeRaiseService {

    private static final Logger log = LoggerFactory.getLogger(DisputeRaiseService.class);

    private static final Set<String> VALID_TYPES = Set.of(
            "NOT_RECEIVED", "WRONG_AMOUNT", "DUPLICATE_CHARGE",
            "FRAUD", "MERCHANT_DISPUTE", "TECHNICAL_ERROR");

    private static final Set<String> ACTIVE_STATUSES = Set.of(
            "OPEN", "UNDER_REVIEW", "ESCALATED");

    private final JdbcTemplate          jdbcTemplate;
    private final DisputeProperties     props;
    private final WebhookEventPublisher webhookPublisher;

    public DisputeRaiseService(JdbcTemplate jdbcTemplate,
                               DisputeProperties props,
                               WebhookEventPublisher webhookPublisher) {
        this.jdbcTemplate     = jdbcTemplate;
        this.props            = props;
        this.webhookPublisher = webhookPublisher;
    }

    @Transactional
    public DisputeRaiseResponse raise(DisputeRaiseRequest req) {

        // 1. Validate type
        if (!VALID_TYPES.contains(req.disputeType())) {
            throw new DisputeTypeInvalidException(req.disputeType());
        }

        // 2. Load original transaction
        Map<String, Object> txn;
        try {
            txn = jdbcTemplate.queryForMap(
                    "SELECT created_at, source_bank, destination_bank FROM transactions WHERE transaction_ref = ? AND status = 'SETTLED' LIMIT 1",
                    req.txnRef());
        } catch (EmptyResultDataAccessException e) {
            throw new DisputeNotFoundException(-1L); // treat missing txn as not-found
        }

        // 3. 90-day window check
        java.sql.Timestamp rawCreatedAt = (java.sql.Timestamp) txn.get("created_at");
        LocalDateTime txnCreatedAt = rawCreatedAt != null ? rawCreatedAt.toLocalDateTime() : LocalDateTime.now();
        if (LocalDateTime.now().isAfter(txnCreatedAt.plusDays(props.getWindowDays()))) {
            throw new DisputeWindowExpiredException(req.txnRef());
        }

        // 4. Check no active dispute for same txnRef
        Integer existing = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM disputes WHERE txn_ref = ? AND status = ANY(ARRAY['OPEN','UNDER_REVIEW','ESCALATED'])",
                Integer.class, req.txnRef());
        if (existing != null && existing > 0) {
            throw new DisputeAlreadyExistsException(req.txnRef());
        }

        // 5. Determine responding PSP from original transaction
        String respondingPspId = (String) txn.get("destination_bank");
        if (respondingPspId == null || respondingPspId.isBlank()) {
            respondingPspId = req.raisingPspId() + "_COUNTERPARTY";
        }

        // 6. Compute SLA deadline
        LocalDateTime now         = LocalDateTime.now();
        LocalDateTime slaDeadline = now.plusDays(props.slaDeadlineDays(req.disputeType()));
        String evidence           = (req.evidence() != null && !req.evidence().isBlank()) ? req.evidence() : "[]";

        // 7. Insert dispute
        Long disputeId = jdbcTemplate.queryForObject(
                """
                INSERT INTO disputes
                    (txn_ref, raising_psp_id, responding_psp_id, dispute_type, status,
                     raised_at, sla_deadline, evidence, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'OPEN', ?, ?, ?, ?, ?)
                RETURNING dispute_id
                """,
                Long.class,
                req.txnRef(), req.raisingPspId(), respondingPspId, req.disputeType(),
                now, slaDeadline, evidence, now, now);

        log.info("Dispute raised: id={} txnRef={} type={} raising={} responding={}",
                disputeId, req.txnRef(), req.disputeType(), req.raisingPspId(), respondingPspId);

        // 8. Fire webhooks to both PSPs
        Map<String, Object> payload = Map.of(
                "disputeId",   disputeId,
                "txnRef",      req.txnRef(),
                "status",      "OPEN",
                "disputeType", req.disputeType(),
                "slaDeadline", slaDeadline.toString());
        webhookPublisher.publishQuietly("DISPUTE.STATUS_CHANGED", req.raisingPspId(), req.txnRef(), payload);
        webhookPublisher.publishQuietly("DISPUTE.STATUS_CHANGED", respondingPspId, req.txnRef(), payload);

        return new DisputeRaiseResponse(disputeId, req.txnRef(), "OPEN", req.disputeType(), slaDeadline, now);
    }
}
