package com.example.switching.liquidity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.example.switching.AbstractIntegrationTest;
import com.example.switching.liquidity.service.LiquidityAlertService;
import com.example.switching.webhook.service.WebhookEventPublisher;

class LiquidityAlertServiceIntegrationTest extends AbstractIntegrationTest {

    private static final String LOW_PSP = "POOL_ALERT_LOW";
    private static final String HEALTHY_PSP = "POOL_ALERT_HEALTHY";

    @Autowired LiquidityAlertService alertService;
    @Autowired JdbcTemplate jdbcTemplate;

    @MockitoBean WebhookEventPublisher webhookPublisher;

    @BeforeEach
    void setup() {
        cleanup();
        jdbcTemplate.update("UPDATE psp_pools SET last_alert_sent_at = NOW()");
        seedPool(LOW_PSP, new BigDecimal("1000.0000"), new BigDecimal("1000.0000"), new BigDecimal("120.00"), null);
        seedPool(HEALTHY_PSP, new BigDecimal("2000.0000"), new BigDecimal("1000.0000"), new BigDecimal("120.00"), null);
        clearInvocations(webhookPublisher);
    }

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("DELETE FROM participants WHERE bank_code IN (?, ?)", LOW_PSP, HEALTHY_PSP);
        clearInvocations(webhookPublisher);
    }

    @Test
    void scanAndPublishLowBalanceAlerts_publishesLowBalanceOnlyAndThrottles() {
        int published = alertService.scanAndPublishLowBalanceAlerts();

        assertEquals(1, published);
        verify(webhookPublisher).liquidityLowAlert(eq(LOW_PSP), anyMap());
        verify(webhookPublisher, never()).liquidityLowAlert(eq(HEALTHY_PSP), anyMap());
        assertNotNull(lastAlertSentAt(LOW_PSP));

        clearInvocations(webhookPublisher);
        int throttled = alertService.scanAndPublishLowBalanceAlerts();

        assertEquals(0, throttled);
        verify(webhookPublisher, never()).liquidityLowAlert(eq(LOW_PSP), anyMap());
    }

    @Test
    void scanAndPublishLowBalanceAlerts_republishesAfterThrottleWindow() {
        jdbcTemplate.update("""
                UPDATE psp_pools
                   SET last_alert_sent_at = ?
                 WHERE psp_id = ?
                """, Timestamp.valueOf(LocalDateTime.now().minusMinutes(16)), LOW_PSP);

        int published = alertService.scanAndPublishLowBalanceAlerts();

        assertEquals(1, published);
        verify(webhookPublisher).liquidityLowAlert(eq(LOW_PSP), anyMap());
    }

    private void seedPool(
            String pspId,
            BigDecimal balance,
            BigDecimal minimumBalance,
            BigDecimal alertThresholdPct,
            LocalDateTime lastAlertSentAt) {
        jdbcTemplate.update("""
                INSERT INTO participants (bank_code, bank_name, status, participant_type, country, currency, created_at)
                VALUES (?, ?, 'ACTIVE', 'DIRECT', 'LA', 'LAK', NOW())
                ON CONFLICT (bank_code) DO NOTHING
                """, pspId, pspId + " Bank");
        jdbcTemplate.update("""
                INSERT INTO psp_pools
                    (psp_id, balance, held_amount, currency, minimum_balance, alert_threshold_pct, last_alert_sent_at)
                VALUES (?, ?, 0, 'LAK', ?, ?, ?)
                ON CONFLICT (psp_id) DO UPDATE
                    SET balance = EXCLUDED.balance,
                        held_amount = 0,
                        minimum_balance = EXCLUDED.minimum_balance,
                        alert_threshold_pct = EXCLUDED.alert_threshold_pct,
                        last_alert_sent_at = EXCLUDED.last_alert_sent_at,
                        last_updated_at = NOW()
                """, pspId, balance, minimumBalance, alertThresholdPct,
                lastAlertSentAt == null ? null : Timestamp.valueOf(lastAlertSentAt));
    }

    private LocalDateTime lastAlertSentAt(String pspId) {
        return jdbcTemplate.queryForObject("""
                SELECT last_alert_sent_at
                  FROM psp_pools
                 WHERE psp_id = ?
                """, (rs, rowNum) -> rs.getTimestamp("last_alert_sent_at").toLocalDateTime(), pspId);
    }
}
