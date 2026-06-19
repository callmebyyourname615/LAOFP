package com.example.switching.liquidity.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.switching.webhook.service.WebhookEventPublisher;

@Profile("!migration")
@Service
public class LiquidityAlertService {

    private static final Logger log = LoggerFactory.getLogger(LiquidityAlertService.class);

    private final JdbcTemplate jdbcTemplate;
    private final WebhookEventPublisher webhookPublisher;

    public LiquidityAlertService(JdbcTemplate jdbcTemplate, WebhookEventPublisher webhookPublisher) {
        this.jdbcTemplate = jdbcTemplate;
        this.webhookPublisher = webhookPublisher;
    }

    @Scheduled(
            initialDelayString = "${switching.liquidity.alert-interval-ms:60000}",
            fixedDelayString = "${switching.liquidity.alert-interval-ms:60000}")
    @Transactional
    public int scanAndPublishLowBalanceAlerts() {
        List<LowLiquidityPool> pools = jdbcTemplate.query("""
                SELECT psp_id, balance, held_amount, available_balance, currency,
                       minimum_balance, alert_threshold_pct
                  FROM psp_pools
                 WHERE available_balance < (minimum_balance * alert_threshold_pct / 100)
                   AND (last_alert_sent_at IS NULL OR last_alert_sent_at < NOW() - INTERVAL '15 minutes')
                 ORDER BY available_balance ASC
                """, (rs, rowNum) -> new LowLiquidityPool(
                rs.getString("psp_id"),
                rs.getBigDecimal("balance"),
                rs.getBigDecimal("held_amount"),
                rs.getBigDecimal("available_balance"),
                rs.getString("currency"),
                rs.getBigDecimal("minimum_balance"),
                rs.getBigDecimal("alert_threshold_pct")));

        for (LowLiquidityPool pool : pools) {
            publishAndThrottle(pool);
        }

        return pools.size();
    }

    private void publishAndThrottle(LowLiquidityPool pool) {
        BigDecimal alertBalance = pool.minimumBalance()
                .multiply(pool.alertThresholdPct())
                .divide(new BigDecimal("100"));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("pspId", pool.pspId());
        data.put("balance", pool.balance());
        data.put("heldAmount", pool.heldAmount());
        data.put("availableBalance", pool.availableBalance());
        data.put("minimumBalance", pool.minimumBalance());
        data.put("alertThresholdPct", pool.alertThresholdPct());
        data.put("alertBalance", alertBalance);
        data.put("currency", pool.currency());
        data.put("detectedAt", LocalDateTime.now().toString());

        webhookPublisher.liquidityLowAlert(pool.pspId(), data);

        jdbcTemplate.update("""
                UPDATE psp_pools
                   SET last_alert_sent_at = NOW(),
                       last_updated_at = NOW()
                 WHERE psp_id = ?
                """, pool.pspId());

        log.warn("Low liquidity alert published: pspId={} available={} threshold={} {}",
                pool.pspId(), pool.availableBalance(), alertBalance, pool.currency());
    }

    private record LowLiquidityPool(
            String pspId,
            BigDecimal balance,
            BigDecimal heldAmount,
            BigDecimal availableBalance,
            String currency,
            BigDecimal minimumBalance,
            BigDecimal alertThresholdPct) {
    }
}
