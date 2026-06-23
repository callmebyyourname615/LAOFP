package com.example.switching.promotion.reconciliation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.example.switching.consistency.ConsistencyAwareReportingJdbcOperations;
import com.example.switching.consistency.ReadConsistency;
import com.example.switching.promotion.reconciliation.PromotionFunderLedgerReconciliationReport.PromotionLedgerItem;

@Service
public class PromotionFunderLedgerReconciliationService {

    private final ConsistencyAwareReportingJdbcOperations jdbc;

    public PromotionFunderLedgerReconciliationService(
            ConsistencyAwareReportingJdbcOperations jdbc) {
        this.jdbc = jdbc;
    }

    public PromotionFunderLedgerReconciliationReport reconcile(
            String funderParticipantId,
            String currency,
            ReadConsistency consistency) {
        ReadConsistency requested = consistency == null
                ? ReadConsistency.STRICT_PRIMARY
                : consistency;
        StringBuilder sql = new StringBuilder(BASE_QUERY);
        List<Object> arguments = new ArrayList<>();
        if (hasText(funderParticipantId)) {
            sql.append(" AND p.funder_participant_id = ?");
            arguments.add(requiredParticipant(funderParticipantId));
        }
        if (hasText(currency)) {
            sql.append(" AND p.currency = ?");
            arguments.add(requiredCurrency(currency));
        }
        sql.append(" ORDER BY p.funder_participant_id, p.currency, p.code");

        List<PromotionLedgerItem> items = jdbc.queryForList(
                        requested, sql.toString(), arguments.toArray())
                .stream()
                .map(PromotionFunderLedgerReconciliationService::mapItem)
                .toList();
        int mismatchCount = (int) items.stream()
                .filter(item -> !"BALANCED".equals(item.status()))
                .count();
        BigDecimal totalConsumed = items.stream()
                .map(PromotionLedgerItem::budgetConsumed)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalSettlementRecorded = items.stream()
                .map(PromotionLedgerItem::settlementRecorded)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new PromotionFunderLedgerReconciliationReport(
                Instant.now(),
                requested,
                mismatchCount == 0 ? "BALANCED" : "MISMATCH",
                items.size(),
                mismatchCount,
                totalConsumed,
                totalSettlementRecorded,
                List.copyOf(items));
    }

    private static PromotionLedgerItem mapItem(Map<String, Object> row) {
        BigDecimal budgetReserved = decimal(row.get("budget_reserved"));
        BigDecimal budgetConsumed = decimal(row.get("budget_consumed"));
        BigDecimal reservedApplications = decimal(row.get("reserved_applications"));
        BigDecimal consumedApplications = decimal(row.get("consumed_applications"));
        BigDecimal settlementRecorded = decimal(row.get("settlement_recorded"));
        BigDecimal settledAmount = decimal(row.get("settled_amount"));
        BigDecimal pendingAmount = decimal(row.get("pending_amount"));
        BigDecimal failedAmount = decimal(row.get("failed_amount"));
        BigDecimal reversedAmount = decimal(row.get("reversed_amount"));
        BigDecimal reservationVariance = budgetReserved.subtract(reservedApplications);
        BigDecimal consumptionVariance = budgetConsumed.subtract(consumedApplications);
        BigDecimal settlementVariance = consumedApplications.subtract(settledAmount);
        boolean balanced = zero(reservationVariance)
                && zero(consumptionVariance)
                && zero(settlementVariance)
                && zero(pendingAmount)
                && zero(failedAmount)
                && zero(reversedAmount);
        return new PromotionLedgerItem(
                uuid(row.get("promotion_id")),
                String.valueOf(row.get("promotion_code")),
                String.valueOf(row.get("funder_participant_id")),
                String.valueOf(row.get("currency")),
                decimal(row.get("budget_cap")),
                budgetReserved,
                budgetConsumed,
                reservedApplications,
                consumedApplications,
                settlementRecorded,
                settledAmount,
                pendingAmount,
                failedAmount,
                reversedAmount,
                reservationVariance,
                consumptionVariance,
                settlementVariance,
                balanced ? "BALANCED" : "MISMATCH");
    }

    private static BigDecimal decimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        return new BigDecimal(String.valueOf(value));
    }

    private static UUID uuid(Object value) {
        return value instanceof UUID id ? id : UUID.fromString(String.valueOf(value));
    }

    private static boolean zero(BigDecimal value) {
        return value.compareTo(BigDecimal.ZERO) == 0;
    }

    private static String requiredParticipant(String value) {
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z0-9_-]{2,64}")) {
            throw new IllegalArgumentException("Invalid funderParticipantId");
        }
        return normalized;
    }

    private static String requiredCurrency(String value) {
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("Invalid currency");
        }
        return normalized;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static final String BASE_QUERY = """
            SELECT p.id AS promotion_id,
                   p.code AS promotion_code,
                   p.funder_participant_id,
                   p.currency,
                   p.budget_cap,
                   p.budget_reserved,
                   p.budget_consumed,
                   COALESCE((
                       SELECT SUM(pa.discount_amount)
                         FROM promotion_application pa
                        WHERE pa.promotion_id = p.id
                          AND pa.status = 'RESERVED'), 0) AS reserved_applications,
                   COALESCE((
                       SELECT SUM(pa.discount_amount)
                         FROM promotion_application pa
                        WHERE pa.promotion_id = p.id
                          AND pa.status = 'CONSUMED'), 0) AS consumed_applications,
                   COALESCE((
                       SELECT SUM(ps.amount)
                         FROM promotion_settlement ps
                         JOIN promotion_application pa
                           ON pa.id = ps.promotion_application_id
                        WHERE pa.promotion_id = p.id), 0) AS settlement_recorded,
                   COALESCE((
                       SELECT SUM(ps.amount)
                         FROM promotion_settlement ps
                         JOIN promotion_application pa
                           ON pa.id = ps.promotion_application_id
                        WHERE pa.promotion_id = p.id
                          AND ps.status = 'SETTLED'), 0) AS settled_amount,
                   COALESCE((
                       SELECT SUM(ps.amount)
                         FROM promotion_settlement ps
                         JOIN promotion_application pa
                           ON pa.id = ps.promotion_application_id
                        WHERE pa.promotion_id = p.id
                          AND ps.status = 'PENDING'), 0) AS pending_amount,
                   COALESCE((
                       SELECT SUM(ps.amount)
                         FROM promotion_settlement ps
                         JOIN promotion_application pa
                           ON pa.id = ps.promotion_application_id
                        WHERE pa.promotion_id = p.id
                          AND ps.status = 'FAILED'), 0) AS failed_amount,
                   COALESCE((
                       SELECT SUM(ps.amount)
                         FROM promotion_settlement ps
                         JOIN promotion_application pa
                           ON pa.id = ps.promotion_application_id
                        WHERE pa.promotion_id = p.id
                          AND ps.status = 'REVERSED'), 0) AS reversed_amount
              FROM promotion p
             WHERE 1 = 1
            """;
}
