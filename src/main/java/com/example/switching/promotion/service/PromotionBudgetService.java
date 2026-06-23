package com.example.switching.promotion.service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.switching.financial.MoneyPrecisionPolicy;
import com.example.switching.promotion.dto.PromotionBudgetReservation;

/** Concurrency-safe budget reservation and funder-ledger service. */
@Service
@ConditionalOnProperty(name = "switching.phase-ii.promotion.enabled", havingValue = "true")
public class PromotionBudgetService {
    private final JdbcTemplate jdbc;

    public PromotionBudgetService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ── Legacy two-arg overloads kept for callers not yet migrated to the explicit
    // reservation lifecycle. They auto-generate transaction reference, default currency
    // and 5-minute expiry, swallow failures into a boolean for back-compat.

    @Transactional
    public boolean reserve(long promotionId, BigDecimal amount) {
        try {
            String txRef = "LEGACY-" + UUID.randomUUID();
            reserve(promotionId, txRef, amount, "LAK", Instant.now().plusSeconds(300));
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    @Transactional
    public boolean reserve(UUID promotionId, BigDecimal amount) {
        return reserve(promotionId.getMostSignificantBits(), amount);
    }

    @Transactional
    public void release(long promotionId, BigDecimal amount) {
        jdbc.update(
                "UPDATE promotion_budget_account SET reserved_amount = GREATEST(reserved_amount - ?, 0),"
                        + " version = version + 1, updated_at = now() WHERE promotion_id = ?",
                amount, promotionId);
    }

    @Transactional
    public void release(UUID promotionId, BigDecimal amount) {
        release(promotionId.getMostSignificantBits(), amount);
    }

    @Transactional
    public void consume(UUID promotionId, BigDecimal amount) {
        // Legacy bulk-consume by promotionId — the explicit per-reservation flow is
        // preferred. This overload only adjusts aggregated counters for back-compat.
        long pid = promotionId.getMostSignificantBits();
        jdbc.update(
                "UPDATE promotion_budget_account SET reserved_amount = GREATEST(reserved_amount - ?, 0),"
                        + " consumed_amount = consumed_amount + ?, version = version + 1, updated_at = now()"
                        + " WHERE promotion_id = ?",
                amount, amount, pid);
    }

    @Transactional
    public PromotionBudgetReservation reserve(long promotionId, String transactionRef,
            BigDecimal amount, String currency, Instant expiresAt) {
        if (promotionId <= 0) throw new IllegalArgumentException("promotionId must be positive");
        requireText(transactionRef, "transactionRef");
        requireText(currency, "currency");
        String normalizedCurrency = currency.trim().toUpperCase(Locale.ROOT);
        if (normalizedCurrency.length() > 8) throw new IllegalArgumentException("currency is too long");
        BigDecimal normalized = MoneyPrecisionPolicy.requirePositive(amount);
        if (expiresAt == null || !expiresAt.isAfter(Instant.now())) {
            throw new IllegalArgumentException("Promotion reservation expiry must be in the future");
        }
        UUID id = jdbc.queryForObject(
                "SELECT reserve_promotion_budget(?, ?, ?, ?, ?)", UUID.class,
                promotionId, transactionRef, normalized, normalizedCurrency,
                Timestamp.from(expiresAt));
        return find(id);
    }

    @Transactional
    public PromotionBudgetReservation consume(UUID reservationId, String idempotencyKey) {
        requireText(idempotencyKey, "idempotencyKey");
        Map<String, Object> row = lock(reservationId);
        String status = (String) row.get("status");
        if ("CONSUMED".equals(status)) return map(row);
        if (!"RESERVED".equals(status)) {
            throw new IllegalStateException("Only RESERVED promotion budget can be consumed");
        }
        BigDecimal amount = (BigDecimal) row.get("amount");
        long promotionId = ((Number) row.get("promotion_id")).longValue();
        int accountUpdated = jdbc.update("""
                UPDATE promotion_budget_account
                   SET reserved_amount = reserved_amount - ?, consumed_amount = consumed_amount + ?,
                       version = version + 1, updated_at = now()
                 WHERE promotion_id = ? AND reserved_amount >= ?
                """, amount, amount, promotionId, amount);
        if (accountUpdated != 1) throw new IllegalStateException("Promotion budget account is inconsistent");
        jdbc.update("""
                UPDATE promotion_budget_reservation
                   SET status='CONSUMED', consumed_at=now()
                 WHERE reservation_id=? AND status='RESERVED'
                """, reservationId);
        jdbc.update("""
                INSERT INTO promotion_funder_ledger
                    (promotion_id, transaction_ref, reservation_id, entry_type, amount, currency, idempotency_key)
                VALUES (?, ?, ?, 'DEBIT', ?, ?, ?)
                ON CONFLICT (idempotency_key) DO NOTHING
                """, promotionId, row.get("transaction_ref"), reservationId, amount,
                row.get("currency"), idempotencyKey);
        return find(reservationId);
    }

    @Transactional
    public PromotionBudgetReservation release(UUID reservationId) {
        Map<String, Object> row = lock(reservationId);
        if (!"RESERVED".equals(row.get("status"))) return map(row);
        BigDecimal amount = (BigDecimal) row.get("amount");
        long promotionId = ((Number) row.get("promotion_id")).longValue();
        jdbc.update("""
                UPDATE promotion_budget_account
                   SET reserved_amount = reserved_amount - ?, version = version + 1, updated_at = now()
                 WHERE promotion_id = ? AND reserved_amount >= ?
                """, amount, promotionId, amount);
        jdbc.update("""
                UPDATE promotion_budget_reservation
                   SET status='RELEASED', released_at=now()
                 WHERE reservation_id=? AND status='RESERVED'
                """, reservationId);
        return find(reservationId);
    }

    @Transactional
    public PromotionBudgetReservation refund(UUID reservationId, String idempotencyKey) {
        requireText(idempotencyKey, "idempotencyKey");
        Map<String, Object> row = lock(reservationId);
        if ("REFUNDED".equals(row.get("status"))) return map(row);
        if (!"CONSUMED".equals(row.get("status"))) {
            throw new IllegalStateException("Only CONSUMED promotion budget can be refunded");
        }
        Long existing = jdbc.queryForObject(
                "SELECT count(*) FROM promotion_funder_ledger WHERE idempotency_key=?", Long.class,
                idempotencyKey);
        if (existing != null && existing > 0) return map(row);
        BigDecimal amount = (BigDecimal) row.get("amount");
        long promotionId = ((Number) row.get("promotion_id")).longValue();
        int updated = jdbc.update("""
                UPDATE promotion_budget_account
                   SET consumed_amount = consumed_amount - ?, version = version + 1, updated_at = now()
                 WHERE promotion_id = ? AND consumed_amount >= ?
                """, amount, promotionId, amount);
        if (updated != 1) throw new IllegalStateException("Promotion consumed budget is inconsistent");
        jdbc.update("""
                INSERT INTO promotion_funder_ledger
                    (promotion_id, transaction_ref, reservation_id, entry_type, amount, currency, idempotency_key)
                VALUES (?, ?, ?, 'CREDIT', ?, ?, ?)
                """, promotionId, row.get("transaction_ref"), reservationId, amount,
                row.get("currency"), idempotencyKey);
        jdbc.update("""
                UPDATE promotion_budget_reservation
                   SET status='REFUNDED', refunded_at=now()
                 WHERE reservation_id=? AND status='CONSUMED'
                """, reservationId);
        return find(reservationId);
    }

    @Scheduled(fixedDelayString = "${switching.phase-ii.promotion.budget-expiry-scan-ms:60000}")
    @Transactional
    public int expireReservations() {
        List<Map<String, Object>> expired = jdbc.queryForList("""
                SELECT reservation_id, promotion_id, amount
                  FROM promotion_budget_reservation
                 WHERE status='RESERVED' AND expires_at <= now()
                 ORDER BY expires_at
                 FOR UPDATE SKIP LOCKED
                 LIMIT 100
                """);
        int count = 0;
        for (Map<String, Object> row : new ArrayList<>(expired)) {
            UUID reservationId = (UUID) row.get("reservation_id");
            long promotionId = ((Number) row.get("promotion_id")).longValue();
            BigDecimal amount = (BigDecimal) row.get("amount");
            int released = jdbc.update("""
                    UPDATE promotion_budget_account
                       SET reserved_amount = reserved_amount - ?, version = version + 1, updated_at=now()
                     WHERE promotion_id=? AND reserved_amount >= ?
                    """, amount, promotionId, amount);
            if (released != 1) throw new IllegalStateException("Promotion budget account is inconsistent during expiry");
            count += jdbc.update("""
                    UPDATE promotion_budget_reservation
                       SET status='EXPIRED', released_at=now()
                     WHERE reservation_id=? AND status='RESERVED'
                    """, reservationId);
        }
        return count;
    }

    @Transactional(readOnly = true)
    public PromotionBudgetReservation find(UUID reservationId) {
        return jdbc.queryForObject("""
                SELECT reservation_id, promotion_id, transaction_ref, amount, currency, status, expires_at
                  FROM promotion_budget_reservation WHERE reservation_id=?
                """, (rs, ignored) -> new PromotionBudgetReservation(
                rs.getObject("reservation_id", UUID.class), rs.getLong("promotion_id"),
                rs.getString("transaction_ref"), rs.getBigDecimal("amount"),
                rs.getString("currency"), rs.getString("status"),
                rs.getTimestamp("expires_at").toInstant()), reservationId);
    }

    private Map<String, Object> lock(UUID reservationId) {
        return jdbc.queryForMap("""
                SELECT reservation_id, promotion_id, transaction_ref, amount, currency, status, expires_at
                  FROM promotion_budget_reservation WHERE reservation_id=? FOR UPDATE
                """, reservationId);
    }

    private PromotionBudgetReservation map(Map<String, Object> row) {
        Object expiry = row.get("expires_at");
        Instant expiresAt = expiry instanceof Timestamp timestamp ? timestamp.toInstant()
                : ((java.time.OffsetDateTime) expiry).toInstant();
        return new PromotionBudgetReservation((UUID) row.get("reservation_id"),
                ((Number) row.get("promotion_id")).longValue(), (String) row.get("transaction_ref"),
                (BigDecimal) row.get("amount"), (String) row.get("currency"),
                (String) row.get("status"), expiresAt);
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
    }
}
