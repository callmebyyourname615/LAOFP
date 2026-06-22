package com.example.switching.promotion.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.switching.common.PhaseIIAuditPublisher;
import com.example.switching.promotion.dto.PromotionApplicationView;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
@ConditionalOnProperty(
        prefix = "switching.phase-ii.promotion",
        name = "enabled",
        havingValue = "true")
public class PromotionApplicationService {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final PromotionEligibilityEvaluator evaluator;
    private final PromotionBudgetService budgets;
    private final PhaseIIAuditPublisher audit;

    public PromotionApplicationService(
            JdbcTemplate jdbc,
            ObjectMapper mapper,
            PromotionEligibilityEvaluator evaluator,
            PromotionBudgetService budgets,
            PhaseIIAuditPublisher audit) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.evaluator = evaluator;
        this.budgets = budgets;
        this.audit = audit;
    }

    @Transactional
    public List<PromotionApplicationView> apply(PromotionContext context) {
        validateContext(context);
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT p.id, p.code, p.priority, p.combinable,
                       p.discount_value, p.discount_mode, p.currency,
                       r.rule_json
                  FROM promotion p
                  JOIN promotion_eligibility_rule r ON r.promotion_id=p.id
                 WHERE p.status='ACTIVE'
                   AND now()>=p.starts_at
                   AND now()<p.ends_at
                   AND p.currency=?
                 ORDER BY p.priority DESC, p.created_at ASC, r.rule_order ASC
                """, context.currency());

        LinkedHashMap<UUID, Map<String, Object>> matched = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            UUID promotionId = (UUID) row.get("id");
            if (matched.containsKey(promotionId)) {
                continue;
            }
            try {
                JsonNode rule = mapper.readTree(String.valueOf(row.get("rule_json")));
                if (evaluator.matches(rule, context)) {
                    matched.put(promotionId, row);
                }
            } catch (Exception exception) {
                throw new IllegalStateException("Invalid persisted promotion rule", exception);
            }
        }

        List<PromotionApplicationView> result = new ArrayList<>();
        BigDecimal remainingFee = context.grossFee().setScale(4, RoundingMode.HALF_UP);
        for (Map<String, Object> promotion : matched.values()) {
            if (remainingFee.signum() <= 0) {
                break;
            }
            UUID promotionId = (UUID) promotion.get("id");
            PromotionApplicationView existing = existingApplication(
                    promotionId,
                    context.transactionReference());
            if (existing != null) {
                if ("RESERVED".equals(existing.status())
                        || "CONSUMED".equals(existing.status())) {
                    result.add(existing);
                    remainingFee = existing.netFee();
                    if (!Boolean.TRUE.equals(promotion.get("combinable"))) {
                        break;
                    }
                }
                continue;
            }

            BigDecimal discount = discount(
                    remainingFee,
                    (BigDecimal) promotion.get("discount_value"),
                    String.valueOf(promotion.get("discount_mode")));
            if (discount.signum() <= 0 || !budgets.reserve(promotionId, discount)) {
                continue;
            }

            BigDecimal netFee = remainingFee.subtract(discount)
                    .setScale(4, RoundingMode.HALF_UP);
            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("participantId", context.participantId());
            evidence.put("channel", context.channel());
            evidence.put("amount", context.amount().toPlainString());
            evidence.put("promotionCode", String.valueOf(promotion.get("code")));
            evidence.put("grossFee", remainingFee.toPlainString());
            evidence.put("discount", discount.toPlainString());
            String evidenceJson = json(evidence);
            UUID applicationId = UUID.randomUUID();
            int inserted = jdbc.update("""
                    INSERT INTO promotion_application(
                        id, promotion_id, transaction_reference,
                        participant_id, channel, gross_fee,
                        discount_amount, net_fee, currency, status,
                        eligibility_evidence, evidence_sha256)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'RESERVED', ?::jsonb, ?)
                    ON CONFLICT(promotion_id, transaction_reference) DO NOTHING
                    """,
                    applicationId,
                    promotionId,
                    context.transactionReference(),
                    context.participantId(),
                    context.channel(),
                    remainingFee,
                    discount,
                    netFee,
                    context.currency(),
                    evidenceJson,
                    sha256(evidenceJson));

            PromotionApplicationView applied;
            if (inserted == 0) {
                // Another transaction won the idempotency race. Return our reservation
                // before observing the durable winner.
                budgets.release(promotionId, discount);
                applied = existingApplication(promotionId, context.transactionReference());
                if (applied == null) {
                    throw new IllegalStateException(
                            "Promotion application conflict completed without a durable row");
                }
                if (!"RESERVED".equals(applied.status())
                        && !"CONSUMED".equals(applied.status())) {
                    continue;
                }
            } else {
                applied = new PromotionApplicationView(
                        applicationId,
                        promotionId,
                        String.valueOf(promotion.get("code")),
                        remainingFee,
                        discount,
                        netFee,
                        context.currency(),
                        "RESERVED");
                audit.publish(
                        "promotion.applied",
                        "PROMOTION_APPLICATION",
                        applicationId.toString(),
                        context.participantId(),
                        evidence);
            }

            result.add(applied);
            remainingFee = applied.netFee();
            if (!Boolean.TRUE.equals(promotion.get("combinable"))) {
                break;
            }
        }
        return List.copyOf(result);
    }

    private PromotionApplicationView existingApplication(
            UUID promotionId,
            String transactionReference) {
        List<PromotionApplicationView> existing = jdbc.query("""
                SELECT pa.id, pa.promotion_id, p.code,
                       pa.gross_fee, pa.discount_amount, pa.net_fee,
                       pa.currency, pa.status
                  FROM promotion_application pa
                  JOIN promotion p ON p.id=pa.promotion_id
                 WHERE pa.promotion_id=?
                   AND pa.transaction_reference=?
                """,
                (resultSet, rowNumber) -> new PromotionApplicationView(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getObject("promotion_id", UUID.class),
                        resultSet.getString("code"),
                        resultSet.getBigDecimal("gross_fee"),
                        resultSet.getBigDecimal("discount_amount"),
                        resultSet.getBigDecimal("net_fee"),
                        resultSet.getString("currency"),
                        resultSet.getString("status")),
                promotionId,
                transactionReference);
        return existing.isEmpty() ? null : existing.getFirst();
    }

    private static BigDecimal discount(
            BigDecimal fee,
            BigDecimal value,
            String mode) {
        BigDecimal discount = "PERCENT".equals(mode)
                ? fee.multiply(value).divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP)
                : value;
        return discount.min(fee).max(BigDecimal.ZERO).setScale(4, RoundingMode.HALF_UP);
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to serialize promotion evidence", exception);
        }
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void validateContext(PromotionContext context) {
        if (context == null
                || context.transactionReference() == null
                || context.transactionReference().isBlank()
                || context.participantId() == null
                || context.participantId().isBlank()
                || context.channel() == null
                || context.channel().isBlank()
                || context.currency() == null
                || !context.currency().matches("[A-Z]{3}")
                || context.amount() == null
                || context.amount().signum() < 0
                || context.grossFee() == null
                || context.grossFee().signum() < 0) {
            throw new IllegalArgumentException("Invalid promotion context");
        }
    }
}
