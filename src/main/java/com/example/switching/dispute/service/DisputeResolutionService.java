package com.example.switching.dispute.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.switching.dispute.dto.DisputeResponse;
import com.example.switching.dispute.entity.DisputeEntity;
import com.example.switching.dispute.exception.DisputeNotFoundException;
import com.example.switching.dispute.exception.DisputeNotAuthorizedException;
import com.example.switching.dispute.repository.DisputeRepository;
import com.example.switching.webhook.service.WebhookEventPublisher;

/**
 * Handles post-raise dispute lifecycle: respond, resolve, and list.
 */
@Service
public class DisputeResolutionService {

    private static final Logger log = LoggerFactory.getLogger(DisputeResolutionService.class);

    private final DisputeRepository      disputeRepo;
    private final JdbcTemplate           jdbcTemplate;
    private final DisputeAutoRefundService autoRefundService;
    private final WebhookEventPublisher  webhookPublisher;

    public DisputeResolutionService(DisputeRepository disputeRepo,
                                    JdbcTemplate jdbcTemplate,
                                    DisputeAutoRefundService autoRefundService,
                                    WebhookEventPublisher webhookPublisher) {
        this.disputeRepo      = disputeRepo;
        this.jdbcTemplate     = jdbcTemplate;
        this.autoRefundService = autoRefundService;
        this.webhookPublisher = webhookPublisher;
    }

    // ── respond ───────────────────────────────────────────────────────────────

    /**
     * Responding PSP adds evidence; dispute moves to UNDER_REVIEW.
     */
    @Transactional
    public DisputeResponse respond(Long disputeId, String callingPspId, String newEvidence) {
        DisputeEntity dispute = load(disputeId);

        // Only the responding PSP can respond
        if (!dispute.getRespondingPspId().equals(callingPspId)) {
            throw new DisputeNotAuthorizedException(disputeId);
        }
        if (!"OPEN".equals(dispute.getStatus())) {
            throw new IllegalStateException("Dispute " + disputeId + " is not OPEN, cannot respond");
        }

        // Merge evidence arrays (simple string append — both must be valid JSON arrays)
        String merged = mergeEvidence(dispute.getEvidence(), newEvidence);
        LocalDateTime now = LocalDateTime.now();

        jdbcTemplate.update(
                "UPDATE disputes SET status='UNDER_REVIEW', evidence=?, updated_at=? WHERE dispute_id=?",
                merged, now, disputeId);

        fireStatusWebhook(dispute, "UNDER_REVIEW");
        log.info("Dispute responded: id={} by={}", disputeId, callingPspId);
        // Build from known values — avoids JPA L1 cache returning stale data after jdbcTemplate update
        return new DisputeResponse(dispute.getDisputeId(), dispute.getTxnRef(),
                dispute.getRaisingPspId(), dispute.getRespondingPspId(),
                dispute.getDisputeType(), "UNDER_REVIEW",
                dispute.getRaisedAt(), dispute.getSlaDeadline(), null,
                merged, null, false);
    }

    // ── resolve ───────────────────────────────────────────────────────────────

    /**
     * Resolve a dispute.  Either PSP can resolve (or it can be called internally
     * for SLA auto-ruling with {@code autoRuled=true}).
     *
     * @param decision   "REFUND" or "NO_ACTION"
     * @param autoRuled  true when called by the SLA enforcement scheduler
     */
    @Transactional
    public DisputeResponse resolve(Long disputeId, String callingPspId,
                                   String decision, String note, boolean autoRuled) {
        DisputeEntity dispute = load(disputeId);

        // Auth check (skipped for SLA auto-ruling)
        if (!autoRuled) {
            boolean isParty = dispute.getRaisingPspId().equals(callingPspId)
                           || dispute.getRespondingPspId().equals(callingPspId);
            if (!isParty) throw new DisputeNotAuthorizedException(disputeId);
        }

        String newStatus = "REFUND".equalsIgnoreCase(decision)
                           ? "RESOLVED_REFUND" : "RESOLVED_NO_ACTION";
        LocalDateTime now = LocalDateTime.now();

        jdbcTemplate.update(
                "UPDATE disputes SET status=?, auto_ruled=?, resolved_at=?, resolution_note=?, updated_at=? WHERE dispute_id=?",
                newStatus, autoRuled, now, note, now, disputeId);

        // Trigger financial refund if needed
        if ("RESOLVED_REFUND".equals(newStatus)) {
            autoRefundService.initiateRefund(disputeId);
        }

        fireStatusWebhook(dispute, newStatus);
        log.info("Dispute resolved: id={} status={} autoRuled={}", disputeId, newStatus, autoRuled);
        // Build from known values — avoids JPA L1 cache returning stale data after jdbcTemplate update
        return new DisputeResponse(dispute.getDisputeId(), dispute.getTxnRef(),
                dispute.getRaisingPspId(), dispute.getRespondingPspId(),
                dispute.getDisputeType(), newStatus,
                dispute.getRaisedAt(), dispute.getSlaDeadline(), now,
                dispute.getEvidence(), note, autoRuled);
    }

    // ── get / list ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public DisputeResponse getDispute(Long disputeId) {
        return toResponse(load(disputeId));
    }

    @Transactional(readOnly = true)
    public List<DisputeResponse> listForPsp(String pspId) {
        return disputeRepo
                .findByRaisingPspIdOrRespondingPspIdOrderByRaisedAtDesc(pspId, pspId)
                .stream().map(this::toResponse).toList();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private DisputeEntity load(Long disputeId) {
        return disputeRepo.findById(disputeId)
                .orElseThrow(() -> new DisputeNotFoundException(disputeId));
    }

    private DisputeResponse toResponse(DisputeEntity d) {
        return new DisputeResponse(
                d.getDisputeId(), d.getTxnRef(),
                d.getRaisingPspId(), d.getRespondingPspId(),
                d.getDisputeType(), d.getStatus(),
                d.getRaisedAt(), d.getSlaDeadline(), d.getResolvedAt(),
                d.getEvidence(), d.getResolutionNote(), d.isAutoRuled());
    }

    private void fireStatusWebhook(DisputeEntity dispute, String newStatus) {
        Map<String, Object> payload = Map.of(
                "disputeId",   dispute.getDisputeId(),
                "txnRef",      dispute.getTxnRef(),
                "status",      newStatus,
                "disputeType", dispute.getDisputeType());
        webhookPublisher.publishQuietly("DISPUTE.STATUS_CHANGED", dispute.getRaisingPspId(),    dispute.getTxnRef(), payload);
        webhookPublisher.publishQuietly("DISPUTE.STATUS_CHANGED", dispute.getRespondingPspId(), dispute.getTxnRef(), payload);
    }

    /** Merge two JSON-array strings: ["a"] + ["b"] → ["a","b"]. */
    private String mergeEvidence(String existing, String incoming) {
        if (incoming == null || incoming.isBlank() || "[]".equals(incoming.trim())) return existing;
        if (existing == null || "[]".equals(existing.trim())) return incoming;
        // Strip trailing ] from existing, strip leading [ from incoming, concat
        String left  = existing.trim();
        String right = incoming.trim();
        return left.substring(0, left.length() - 1) + "," + right.substring(1);
    }
}
