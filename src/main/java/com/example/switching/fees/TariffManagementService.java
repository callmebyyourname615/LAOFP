package com.example.switching.fees;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class TariffManagementService {
    private final JdbcTemplate jdbc;

    public TariffManagementService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public TariffPlanSummaryResponse create(CreateTariffRequest request, String actor) {
        validate(request);
        String planCode = normalizePlanCode(request.planCode(), request.name());
        UUID planId = findOrCreatePlan(planCode, request.name(), request.participantCode());
        int versionNo = request.version() == null ? nextVersion(planId) : request.version();
        if (versionNo <= 0) {
            throw new IllegalArgumentException("version must be greater than zero");
        }
        UUID versionId = UUID.randomUUID();
        String requestedBy = StringUtils.hasText(actor) ? actor : "unknown";
        Instant validFrom = request.effectiveFrom() == null ? Instant.now() : request.effectiveFrom();
        String contentHash = hash(planCode + "|" + versionNo + "|" + request.rules().toString());

        jdbc.update("""
                INSERT INTO tariff_version(
                    id, plan_id, version_no, status, valid_from, valid_until,
                    requested_by, content_hash)
                VALUES (?, ?, ?, 'DRAFT', ?, ?, ?, ?)
                """,
                versionId,
                planId,
                versionNo,
                toOffsetDateTime(validFrom),
                toOffsetDateTime(request.validUntil()),
                requestedBy,
                contentHash);

        int priority = 100;
        for (CreateTariffRuleRequest rule : request.rules()) {
            insertRule(versionId, rule, priority++);
        }
        return findSummary(versionId);
    }

    private UUID findOrCreatePlan(String planCode, String description, String participantCode) {
        List<UUID> existing = jdbc.query(
                "SELECT id FROM tariff_plan WHERE plan_code = ?",
                (rs, rowNum) -> rs.getObject("id", UUID.class),
                planCode);
        if (!existing.isEmpty()) {
            return existing.get(0);
        }
        UUID planId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO tariff_plan(id, plan_code, description, participant_code)
                VALUES (?, ?, ?, ?)
                """,
                planId,
                planCode,
                StringUtils.hasText(description) ? description.trim() : planCode,
                normalizeOptional(participantCode));
        return planId;
    }

    private int nextVersion(UUID planId) {
        Integer value = jdbc.queryForObject(
                "SELECT coalesce(max(version_no), 0) + 1 FROM tariff_version WHERE plan_id = ?",
                Integer.class,
                planId);
        return value == null ? 1 : value;
    }

    private void insertRule(UUID versionId, CreateTariffRuleRequest rule, int defaultPriority) {
        String messageType = normalizeRequired(firstNonBlank(rule.messageType(), rule.channel()), "messageType");
        String currency = normalizeRequired(rule.currency(), "currency");
        if (!currency.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("currency must be an ISO 4217 code");
        }
        BigDecimal feeValue = nvl(rule.feeValue(), BigDecimal.ZERO);
        BigDecimal flatFee = nvl(rule.flatFee(), BigDecimal.ZERO);
        BigDecimal rateBasisPoints = nvl(rule.rateBasisPoints(), BigDecimal.ZERO);
        String feeType = normalizeOptional(rule.feeType());
        if ("FIXED".equals(feeType)) {
            flatFee = feeValue;
        } else if ("PERCENT".equals(feeType)) {
            rateBasisPoints = feeValue.multiply(new BigDecimal("100"));
        } else if (StringUtils.hasText(feeType)) {
            throw new IllegalArgumentException("feeType must be FIXED or PERCENT");
        }

        jdbc.update("""
                INSERT INTO tariff_rule(
                    id, tariff_version_id, message_type, currency,
                    minimum_amount, maximum_amount, flat_fee, rate_basis_points,
                    minimum_fee, maximum_fee, priority)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                versionId,
                messageType,
                currency,
                nvl(firstNonNull(rule.minimumAmount(), rule.minAmount()), BigDecimal.ZERO),
                firstNonNull(rule.maximumAmount(), rule.maxAmount()),
                flatFee,
                rateBasisPoints,
                nvl(rule.minimumFee(), BigDecimal.ZERO),
                rule.maximumFee(),
                rule.priority() == null ? defaultPriority : rule.priority());
    }

    private TariffPlanSummaryResponse findSummary(UUID versionId) {
        return jdbc.queryForObject("""
                SELECT tv.id, tp.plan_code, tv.version_no, tv.status, tv.valid_from,
                       count(tr.id)::int AS rule_count
                  FROM tariff_version tv
                  JOIN tariff_plan tp ON tp.id = tv.plan_id
                  LEFT JOIN tariff_rule tr ON tr.tariff_version_id = tv.id
                 WHERE tv.id = ?
                 GROUP BY tv.id, tp.plan_code, tv.version_no, tv.status, tv.valid_from
                """,
                (rs, rowNum) -> new TariffPlanSummaryResponse(
                        rs.getObject("id", UUID.class),
                        rs.getString("plan_code"),
                        rs.getInt("version_no"),
                        rs.getString("status"),
                        rs.getObject("valid_from", java.time.OffsetDateTime.class),
                        rs.getInt("rule_count")),
                versionId);
    }

    private void validate(CreateTariffRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }
        if (!StringUtils.hasText(request.name()) && !StringUtils.hasText(request.planCode())) {
            throw new IllegalArgumentException("name or planCode is required");
        }
        if (request.rules() == null || request.rules().isEmpty()) {
            throw new IllegalArgumentException("at least one tariff rule is required");
        }
    }

    private static String normalizePlanCode(String planCode, String name) {
        String raw = StringUtils.hasText(planCode) ? planCode : name;
        return normalizeRequired(raw, "planCode")
                .replaceAll("[^A-Z0-9_]+", "_")
                .replaceAll("_+", "_");
    }

    private static String normalizeRequired(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizeOptional(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : null;
    }

    private static String firstNonBlank(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    private static BigDecimal firstNonNull(BigDecimal first, BigDecimal second) {
        return first != null ? first : second;
    }

    private static BigDecimal nvl(BigDecimal value, BigDecimal fallback) {
        return value == null ? fallback : value;
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    public record CreateTariffRequest(
            String planCode,
            String name,
            Integer version,
            String participantCode,
            Instant effectiveFrom,
            Instant validUntil,
            List<CreateTariffRuleRequest> rules) {}

    public record CreateTariffRuleRequest(
            String name,
            String messageType,
            String channel,
            String currency,
            String feeType,
            BigDecimal feeValue,
            BigDecimal flatFee,
            BigDecimal rateBasisPoints,
            BigDecimal minimumAmount,
            BigDecimal maximumAmount,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            BigDecimal minimumFee,
            BigDecimal maximumFee,
            Integer priority) {}
}
