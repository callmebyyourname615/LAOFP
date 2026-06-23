package com.example.switching.phaseii;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.example.switching.AbstractIntegrationTest;
import com.example.switching.promotion.service.PromotionBudgetService;

class PromotionBudgetConcurrencyIntegrationTest extends AbstractIntegrationTest {

    private static final BigDecimal RESERVATION = new BigDecimal("10.0000");
    private static final int REQUESTS = 40;
    private static final int EXPECTED_ACCEPTED = 10;

    @Autowired JdbcTemplate jdbc;
    @Autowired PromotionBudgetService budgets;

    private UUID promotionId;

    @BeforeEach
    void setup() {
        jdbc.update("DELETE FROM promotion_settlement");
        jdbc.update("DELETE FROM promotion_application");
        jdbc.update("DELETE FROM promotion_eligibility_rule");
        jdbc.update("DELETE FROM promotion");
        promotionId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO promotion(
                    id, code, name, promotion_type, status, priority, combinable,
                    funder_participant_id, currency, budget_cap, budget_reserved,
                    budget_consumed, discount_value, discount_mode, starts_at,
                    ends_at, created_by)
                VALUES (?, ?, 'Phase 70 concurrency', 'WAIVER', 'ACTIVE', 100, false,
                        'BANK_A', 'LAK', 100, 0, 0, 10, 'FIXED',
                        now() - interval '1 minute', now() + interval '1 day', 'test')
                """, promotionId, "P-" + promotionId);
    }

    @Test
    void concurrentReservationsNeverOverspendAndLedgerRemainsExact() throws Exception {
        CountDownLatch ready = new CountDownLatch(20);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();

        try (var executor = Executors.newFixedThreadPool(20)) {
            for (int index = 0; index < REQUESTS; index++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return budgets.reserve(promotionId, RESERVATION);
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            int accepted = 0;
            for (Future<Boolean> future : futures) {
                if (future.get(Duration.ofSeconds(20).toMillis(), TimeUnit.MILLISECONDS)) {
                    accepted++;
                }
            }
            assertThat(accepted).isEqualTo(EXPECTED_ACCEPTED);
        }

        Map<String, Object> row = jdbc.queryForMap("""
                SELECT budget_reserved, budget_consumed, budget_cap
                  FROM promotion
                 WHERE id = ?
                """, promotionId);
        BigDecimal reserved = (BigDecimal) row.get("budget_reserved");
        BigDecimal consumed = (BigDecimal) row.get("budget_consumed");
        BigDecimal cap = (BigDecimal) row.get("budget_cap");

        assertThat(reserved).isEqualByComparingTo(RESERVATION.multiply(BigDecimal.valueOf(EXPECTED_ACCEPTED)));
        assertThat(consumed).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(reserved.add(consumed)).isEqualByComparingTo(cap);
        assertThat(reserved.add(consumed)).isLessThanOrEqualTo(cap);
    }
}
