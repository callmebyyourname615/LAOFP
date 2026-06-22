package com.example.switching.promotion.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.switching.common.PhaseIIAuditPublisher;
import com.example.switching.promotion.dto.CreatePromotionRequest;
import com.example.switching.promotion.dto.PromotionResponse;
import com.example.switching.promotion.entity.PromotionEntity;
import com.example.switching.promotion.enums.PromotionStatus;
import com.example.switching.promotion.repository.PromotionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
@ConditionalOnProperty(
        prefix = "switching.phase-ii.promotion",
        name = "enabled",
        havingValue = "true")
public class PromotionManagementService {

    private final PromotionRepository repository;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final PhaseIIAuditPublisher audit;

    public PromotionManagementService(
            PromotionRepository repository,
            JdbcTemplate jdbc,
            ObjectMapper mapper,
            PhaseIIAuditPublisher audit) {
        this.repository = repository;
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.audit = audit;
    }

    @Transactional
    public PromotionResponse create(CreatePromotionRequest request, String actor) {
        validate(request);
        PromotionEntity promotion = new PromotionEntity();
        promotion.setId(UUID.randomUUID());
        promotion.setCode(request.code().trim().toUpperCase(Locale.ROOT));
        promotion.setName(request.name().trim());
        promotion.setPromotionType(request.type());
        promotion.setStatus(PromotionStatus.DRAFT);
        promotion.setPriority(request.priority());
        promotion.setCombinable(request.combinable());
        promotion.setFunderParticipantId(request.funderParticipantId().trim());
        promotion.setCurrency(request.currency());
        promotion.setBudgetCap(request.budgetCap());
        promotion.setBudgetReserved(BigDecimal.ZERO);
        promotion.setBudgetConsumed(BigDecimal.ZERO);
        promotion.setDiscountValue(request.discountValue());
        promotion.setDiscountMode(request.discountMode());
        promotion.setStartsAt(request.startsAt());
        promotion.setEndsAt(request.endsAt());
        promotion.setCreatedBy(requiredActor(actor));
        repository.saveAndFlush(promotion);

        int ruleOrder = 0;
        for (var rule : request.eligibilityRules()) {
            try {
                String json = mapper.writeValueAsString(rule);
                jdbc.update("""
                        INSERT INTO promotion_eligibility_rule(
                            id, promotion_id, rule_order,
                            rule_json, rule_sha256)
                        VALUES (?, ?, ?, ?::jsonb, ?)
                        """,
                        UUID.randomUUID(),
                        promotion.getId(),
                        ++ruleOrder,
                        json,
                        sha256(json));
            } catch (Exception exception) {
                throw new IllegalArgumentException("Invalid eligibility rule", exception);
            }
        }
        audit.publish(
                "promotion.created",
                "PROMOTION",
                promotion.getId().toString(),
                actor,
                Map.of(
                        "code", promotion.getCode(),
                        "type", promotion.getPromotionType().name(),
                        "status", promotion.getStatus().name()));
        return view(promotion);
    }

    @Transactional
    public PromotionResponse activate(UUID id, String approver) {
        PromotionEntity promotion = lock(id);
        if (promotion.getStatus() != PromotionStatus.DRAFT) {
            throw new IllegalStateException("Only DRAFT promotions can be activated");
        }
        String actor = requiredActor(approver);
        if (actor.equals(promotion.getCreatedBy())) {
            throw new IllegalArgumentException("Promotion creator cannot approve activation");
        }
        if (!promotion.getEndsAt().isAfter(Instant.now())) {
            throw new IllegalStateException("Expired promotion cannot be activated");
        }
        promotion.setStatus(PromotionStatus.ACTIVE);
        PromotionEntity saved = repository.saveAndFlush(promotion);
        audit.publish(
                "promotion.activated",
                "PROMOTION",
                id.toString(),
                actor,
                Map.of("code", saved.getCode(), "status", saved.getStatus().name()));
        return view(saved);
    }

    @Transactional
    public PromotionResponse suspend(UUID id, String actor) {
        PromotionEntity promotion = lock(id);
        if (promotion.getStatus() != PromotionStatus.ACTIVE) {
            throw new IllegalStateException("Only ACTIVE promotions can be suspended");
        }
        promotion.setStatus(PromotionStatus.SUSPENDED);
        promotion.setSuspendedBy(requiredActor(actor));
        promotion.setSuspendedAt(Instant.now());
        PromotionEntity saved = repository.saveAndFlush(promotion);
        audit.publish(
                "promotion.suspended",
                "PROMOTION",
                id.toString(),
                actor,
                Map.of("code", saved.getCode(), "status", saved.getStatus().name()));
        return view(saved);
    }

    @Transactional
    public PromotionResponse extend(UUID id, Instant endsAt, String actor) {
        PromotionEntity promotion = lock(id);
        if (endsAt == null || !endsAt.isAfter(promotion.getEndsAt())) {
            throw new IllegalArgumentException("Extension must move end forward");
        }
        if (promotion.getStatus() == PromotionStatus.CLOSED
                || promotion.getStatus() == PromotionStatus.EXPIRED) {
            throw new IllegalStateException("Closed or expired promotion cannot be extended");
        }
        promotion.setEndsAt(endsAt);
        PromotionEntity saved = repository.saveAndFlush(promotion);
        audit.publish(
                "promotion.extended",
                "PROMOTION",
                id.toString(),
                actor,
                Map.of("code", saved.getCode(), "endsAt", endsAt.toString()));
        return view(saved);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> report(UUID id) {
        PromotionEntity promotion = repository.findById(id).orElseThrow();
        Map<String, Object> usage = jdbc.queryForMap("""
                SELECT count(*) FILTER (WHERE status='CONSUMED') AS consumed_applications,
                       count(*) FILTER (WHERE status='RESERVED') AS reserved_applications,
                       coalesce(sum(discount_amount) FILTER (WHERE status='CONSUMED'), 0)
                           AS consumed_discount,
                       coalesce(sum(discount_amount) FILTER (WHERE status='RESERVED'), 0)
                           AS reserved_discount
                  FROM promotion_application
                 WHERE promotion_id=?
                """, id);
        return Map.of("promotion", view(promotion), "usage", usage);
    }

    private PromotionEntity lock(UUID id) {
        return repository.findByIdForUpdate(id).orElseThrow();
    }

    private static void validate(CreatePromotionRequest request) {
        if (!request.endsAt().isAfter(request.startsAt())) {
            throw new IllegalArgumentException("Promotion end must be after start");
        }
        if (request.budgetCap().signum() < 0 || request.discountValue().signum() < 0) {
            throw new IllegalArgumentException("Promotion amounts cannot be negative");
        }
        if ("PERCENT".equals(request.discountMode())
                && request.discountValue().compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("Percentage discount cannot exceed 100");
        }
    }

    private static PromotionResponse view(PromotionEntity promotion) {
        return new PromotionResponse(
                promotion.getId(),
                promotion.getCode(),
                promotion.getName(),
                promotion.getPromotionType(),
                promotion.getStatus(),
                promotion.getPriority(),
                promotion.isCombinable(),
                promotion.getFunderParticipantId(),
                promotion.getCurrency(),
                promotion.getBudgetCap(),
                promotion.getBudgetReserved(),
                promotion.getBudgetConsumed(),
                promotion.getBudgetCap()
                        .subtract(promotion.getBudgetReserved())
                        .subtract(promotion.getBudgetConsumed()),
                promotion.getDiscountValue(),
                promotion.getDiscountMode(),
                promotion.getStartsAt(),
                promotion.getEndsAt());
    }

    private static String requiredActor(String actor) {
        if (actor == null || actor.isBlank()) {
            throw new IllegalArgumentException("Actor is required");
        }
        return actor;
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
}
