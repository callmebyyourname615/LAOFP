package com.example.switching.promotion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import com.example.switching.AbstractIntegrationTest;

@TestPropertySource(properties = "switching.phase-ii.promotion.enabled=true")
class PromotionBudgetServiceIntegrationTest extends AbstractIntegrationTest {
    @Autowired PromotionBudgetService service;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        jdbc.update("DELETE FROM promotion_funder_ledger");
        jdbc.update("DELETE FROM promotion_budget_reservation");
        jdbc.update("DELETE FROM promotion_budget_account");
        jdbc.update("""
                INSERT INTO promotion_budget_account(promotion_id,currency,budget_cap)
                VALUES (62001,'LAK',1000.0000)
                """);
    }

    @Test
    void reservationIsIdempotentAndCannotExceedBudget() {
        var first = service.reserve(62001, "TX-62-1", new BigDecimal("600"), "LAK",
                Instant.now().plus(10, ChronoUnit.MINUTES));
        var retry = service.reserve(62001, "TX-62-1", new BigDecimal("600"), "LAK",
                Instant.now().plus(10, ChronoUnit.MINUTES));
        assertThat(retry.reservationId()).isEqualTo(first.reservationId());
        assertThatThrownBy(() -> service.reserve(62001, "TX-62-1", new BigDecimal("601"), "LAK",
                Instant.now().plus(10, ChronoUnit.MINUTES)))
                .isInstanceOf(Exception.class);
        assertThatThrownBy(() -> service.reserve(62001, "TX-62-2", new BigDecimal("500"), "LAK",
                Instant.now().plus(10, ChronoUnit.MINUTES)))
                .isInstanceOf(Exception.class);
    }

    @Test
    void consumeAndRefundMaintainFunderLedger() {
        var reservation = service.reserve(62001, "TX-62-3", new BigDecimal("125.50"), "LAK",
                Instant.now().plus(10, ChronoUnit.MINUTES));
        assertThat(service.consume(reservation.reservationId(), "PROMO-DEBIT-TX-62-3").status())
                .isEqualTo("CONSUMED");
        assertThat(service.refund(reservation.reservationId(), "PROMO-CREDIT-TX-62-3").status())
                .isEqualTo("REFUNDED");
        assertThat(service.refund(reservation.reservationId(), "PROMO-CREDIT-TX-62-3").status())
                .isEqualTo("REFUNDED");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM promotion_funder_ledger WHERE transaction_ref='TX-62-3'",
                Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT consumed_amount FROM promotion_budget_account WHERE promotion_id=62001",
                BigDecimal.class)).isEqualByComparingTo("0.0000");
    }

    @Test
    void expiredReservationsReleaseBudgetExactlyOnce() {
        var reservation = service.reserve(62001, "TX-62-EXP", new BigDecimal("200"), "lak",
                Instant.now().plus(10, ChronoUnit.MINUTES));
        jdbc.update("UPDATE promotion_budget_reservation SET expires_at=now()-interval '1 minute' WHERE reservation_id=?",
                reservation.reservationId());
        assertThat(service.expireReservations()).isEqualTo(1);
        assertThat(service.expireReservations()).isZero();
        assertThat(service.find(reservation.reservationId()).status()).isEqualTo("EXPIRED");
        assertThat(jdbc.queryForObject(
                "SELECT reserved_amount FROM promotion_budget_account WHERE promotion_id=62001",
                BigDecimal.class)).isEqualByComparingTo("0.0000");
    }
}
