package com.example.switching.promotion.service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("!migration")
@ConditionalOnProperty(
        prefix = "switching.phase-ii.promotion",
        name = "enabled",
        havingValue = "true")
public class PromotionReservationCleanupScheduler {

    private final JdbcTemplate jdbc;
    private final PromotionBudgetService budgets;
    private final Duration reservationTtl;

    public PromotionReservationCleanupScheduler(
            JdbcTemplate jdbc,
            PromotionBudgetService budgets,
            @Value("${switching.phase-ii.promotion.reservation-ttl:PT15M}")
                    Duration reservationTtl) {
        this.jdbc = jdbc;
        this.budgets = budgets;
        this.reservationTtl = reservationTtl;
    }

    @Scheduled(fixedDelayString = "${switching.phase-ii.promotion.cleanup-poll-ms:60000}")
    @Transactional
    public void releaseExpiredReservations() {
        if (reservationTtl.isZero() || reservationTtl.isNegative()) {
            throw new IllegalStateException("Promotion reservation TTL must be positive");
        }
        List<Map<String, Object>> expired = jdbc.queryForList("""
                SELECT id, promotion_id, discount_amount
                  FROM promotion_application
                 WHERE status='RESERVED'
                   AND reserved_at < now() - (? * interval '1 millisecond')
                 ORDER BY reserved_at
                 LIMIT 500
                 FOR UPDATE SKIP LOCKED
                """, reservationTtl.toMillis());
        for (Map<String, Object> application : expired) {
            UUID applicationId = (UUID) application.get("id");
            budgets.release(
                    (UUID) application.get("promotion_id"),
                    (java.math.BigDecimal) application.get("discount_amount"));
            jdbc.update("""
                    UPDATE promotion_application
                       SET status='RELEASED', released_at=now()
                     WHERE id=? AND status='RESERVED'
                    """, applicationId);
        }
    }
}
