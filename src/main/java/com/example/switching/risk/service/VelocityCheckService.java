package com.example.switching.risk.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.example.switching.risk.config.RiskProperties;
import com.example.switching.risk.dto.VelocityResult;

/**
 * Maintains sliding-window velocity counters per PSP in the {@code velocity_checks} table.
 *
 * <p>Each check type maps to one window row. On every call to {@code checkVelocity}:
 * <ol>
 *   <li>Truncate {@code now} to the window boundary (hour / day).</li>
 *   <li>Upsert the row: increment {@code current_value}, set {@code breached}.</li>
 *   <li>Return {@link VelocityResult} — caller decides whether to block.</li>
 * </ol>
 *
 * <p>The upsert is: {@code INSERT … ON CONFLICT (psp_id, check_type, window_start) DO UPDATE}.
 * This is safe under concurrent requests since PostgreSQL serialises the upsert per row.
 */
@Service
public class VelocityCheckService {

    private static final Logger log = LoggerFactory.getLogger(VelocityCheckService.class);

    private static final String UPSERT_SQL = """
            INSERT INTO velocity_checks
                (psp_id, check_type, window_start, window_end, current_value, limit_value, breached, last_updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, NOW())
            ON CONFLICT (psp_id, check_type, window_start)
            DO UPDATE SET
                current_value   = velocity_checks.current_value + EXCLUDED.current_value,
                limit_value     = EXCLUDED.limit_value,
                breached        = (velocity_checks.current_value + EXCLUDED.current_value) > EXCLUDED.limit_value,
                last_updated_at = NOW()
            RETURNING current_value, limit_value, breached
            """;

    private final JdbcTemplate jdbcTemplate;
    private final RiskProperties riskProperties;

    public VelocityCheckService(JdbcTemplate jdbcTemplate, RiskProperties riskProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.riskProperties = riskProperties;
    }

    /**
     * Check all velocity limits for a transfer and return the first breach found.
     *
     * @param pspId  sending PSP identifier
     * @param amount transfer amount in LAK
     * @return {@link VelocityResult} — {@code withinLimits=true} if all checks pass
     */
    public VelocityResult checkVelocity(String pspId, BigDecimal amount) {
        LocalDateTime now = LocalDateTime.now();

        // 1. COUNT_HOURLY — transaction count in last 1 h
        LocalDateTime hourStart = now.truncatedTo(ChronoUnit.HOURS);
        LocalDateTime hourEnd   = hourStart.plusHours(1);
        VelocityResult hourlyCount = upsertAndCheck(
                pspId, "COUNT_HOURLY", hourStart, hourEnd,
                1.0, riskProperties.getVelocityHourlyMaxCount());
        if (!hourlyCount.isWithinLimits()) {
            return hourlyCount;
        }

        // 2. COUNT_DAILY — transaction count in last 24 h
        LocalDateTime dayStart = now.truncatedTo(ChronoUnit.DAYS);
        LocalDateTime dayEnd   = dayStart.plusDays(1);
        VelocityResult dailyCount = upsertAndCheck(
                pspId, "COUNT_DAILY", dayStart, dayEnd,
                1.0, riskProperties.getVelocityDailyMaxCount());
        if (!dailyCount.isWithinLimits()) {
            return dailyCount;
        }

        // 3. AMOUNT_DAILY — total LAK amount in last 24 h
        double amountDouble = amount == null ? 0.0 : amount.doubleValue();
        VelocityResult dailyAmount = upsertAndCheck(
                pspId, "AMOUNT_DAILY", dayStart, dayEnd,
                amountDouble, riskProperties.getVelocityDailyMaxAmountLak());
        if (!dailyAmount.isWithinLimits()) {
            return dailyAmount;
        }

        return VelocityResult.ok();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private VelocityResult upsertAndCheck(String pspId, String checkType,
                                          LocalDateTime windowStart, LocalDateTime windowEnd,
                                          double increment, double limit) {
        try {
            // initial breached = false on first INSERT; recomputed in DO UPDATE
            boolean initialBreached = increment > limit;
            return jdbcTemplate.queryForObject(UPSERT_SQL,
                    (rs, rowNum) -> {
                        double current = rs.getDouble("current_value");
                        double lim     = rs.getDouble("limit_value");
                        boolean breach = rs.getBoolean("breached");
                        if (breach) {
                            log.warn("Velocity limit breached: psp={} type={} current={} limit={}",
                                    pspId, checkType, current, lim);
                            return VelocityResult.breached(checkType, current, lim);
                        }
                        return VelocityResult.ok();
                    },
                    pspId, checkType, windowStart, windowEnd, increment, limit, initialBreached);
        } catch (Exception e) {
            // Fail-open: if velocity DB is unavailable, do not block the transaction
            log.warn("Velocity check failed (fail-open): psp={} type={} error={}",
                    pspId, checkType, e.getMessage());
            return VelocityResult.ok();
        }
    }
}
