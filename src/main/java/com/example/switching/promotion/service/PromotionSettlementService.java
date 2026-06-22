package com.example.switching.promotion.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.switching.common.PhaseIIAuditPublisher;
import com.example.switching.webhook.service.WebhookEventPublisher;

@Service
@ConditionalOnProperty(
        prefix = "switching.phase-ii.promotion",
        name = "enabled",
        havingValue = "true")
public class PromotionSettlementService {

    private final JdbcTemplate jdbc;
    private final PromotionBudgetService budgets;
    private final WebhookEventPublisher webhook;
    private final PhaseIIAuditPublisher audit;

    public PromotionSettlementService(
            JdbcTemplate jdbc,
            PromotionBudgetService budgets,
            WebhookEventPublisher webhook,
            PhaseIIAuditPublisher audit) {
        this.jdbc = jdbc;
        this.budgets = budgets;
        this.webhook = webhook;
        this.audit = audit;
    }

    @Transactional
    public UUID settle(
            UUID applicationId,
            String beneficiaryParticipantId) {
        String beneficiary = requiredParticipant(beneficiaryParticipantId);
        Map<String, Object> row = jdbc.queryForMap("""
                SELECT pa.promotion_id,
                       pa.discount_amount,
                       pa.currency,
                       pa.status,
                       p.funder_participant_id,
                       p.code
                  FROM promotion_application pa
                  JOIN promotion p ON p.id=pa.promotion_id
                 WHERE pa.id=?
                 FOR UPDATE OF pa, p
                """, applicationId);
        String applicationStatus = String.valueOf(row.get("status"));
        if ("CONSUMED".equals(applicationStatus)) {
            List<UUID> existing = jdbc.queryForList("""
                    SELECT id
                      FROM promotion_settlement
                     WHERE promotion_application_id=?
                    """, UUID.class, applicationId);
            if (existing.size() != 1) {
                throw new IllegalStateException(
                        "Consumed promotion application has no unique settlement");
            }
            return existing.getFirst();
        }
        if (!"RESERVED".equals(applicationStatus)) {
            throw new IllegalStateException("Promotion application is not reserved");
        }

        UUID promotionId = (UUID) row.get("promotion_id");
        BigDecimal amount = (BigDecimal) row.get("discount_amount");
        budgets.consume(promotionId, amount);
        UUID settlementId = UUID.randomUUID();
        String settlementReference = "PROMO-" + applicationId;
        jdbc.update("""
                INSERT INTO promotion_settlement(
                    id, promotion_application_id,
                    funder_participant_id, beneficiary_participant_id,
                    amount, currency, settlement_reference,
                    status, settled_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'SETTLED', now())
                """,
                settlementId,
                applicationId,
                row.get("funder_participant_id"),
                beneficiary,
                amount,
                row.get("currency"),
                settlementReference);
        int changed = jdbc.update("""
                UPDATE promotion_application
                   SET status='CONSUMED', consumed_at=now()
                 WHERE id=? AND status='RESERVED'
                """, applicationId);
        if (changed != 1) {
            throw new IllegalStateException("Promotion reservation claim was lost");
        }

        Map<String, Object> evidence = Map.of(
                "promotionCode", String.valueOf(row.get("code")),
                "amount", amount.toPlainString(),
                "currency", String.valueOf(row.get("currency")),
                "beneficiaryParticipantId", beneficiary,
                "settlementReference", settlementReference);
        audit.publish(
                "promotion.settled",
                "PROMOTION_SETTLEMENT",
                settlementId.toString(),
                "SYSTEM",
                evidence);
        webhook.publishQuietly(
                "promotion.settled",
                beneficiary,
                settlementReference,
                evidence);
        return settlementId;
    }

    @Transactional
    public void release(UUID applicationId) {
        Map<String, Object> row = jdbc.queryForMap("""
                SELECT promotion_id, discount_amount, status
                  FROM promotion_application
                 WHERE id=?
                 FOR UPDATE
                """, applicationId);
        if (!"RESERVED".equals(String.valueOf(row.get("status")))) {
            return;
        }
        UUID promotionId = (UUID) row.get("promotion_id");
        BigDecimal amount = (BigDecimal) row.get("discount_amount");
        budgets.release(promotionId, amount);
        int changed = jdbc.update("""
                UPDATE promotion_application
                   SET status='RELEASED', released_at=now()
                 WHERE id=? AND status='RESERVED'
                """, applicationId);
        if (changed != 1) {
            throw new IllegalStateException("Promotion release claim was lost");
        }
        audit.publish(
                "promotion.released",
                "PROMOTION_APPLICATION",
                applicationId.toString(),
                "SYSTEM",
                Map.of(
                        "promotionId", promotionId.toString(),
                        "amount", amount.toPlainString()));
    }

    private static String requiredParticipant(String value) {
        if (value == null
                || !value.matches("[A-Za-z0-9_-]{2,64}")) {
            throw new IllegalArgumentException("Invalid beneficiary participant");
        }
        return value;
    }
}
