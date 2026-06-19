package com.example.switching.maintenance.service;

import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled triggers for {@link AggregationService}.
 *
 * <p>Two schedules run independently:
 * <ul>
 *   <li><b>End-of-day (daily)</b> — runs at 00:30 every night to aggregate the
 *       previous day's complete data.  Cron configured via
 *       {@code switching.aggregation.daily-cron} (default: {@code 0 30 0 * * *}).</li>
 *   <li><b>Intraday (hourly)</b> — runs every hour to keep today's hourly and
 *       daily summaries reasonably fresh.  Cron configured via
 *       {@code switching.aggregation.hourly-cron} (default: {@code 0 5 * * * *}).</li>
 * </ul>
 *
 * <p>Both jobs are guarded by {@link SchedulerLockService} to prevent concurrent
 * execution across multiple replicas (leader-election via DB advisory lock).
 */
@Component
public class AggregationScheduler {

    private static final Logger log = LoggerFactory.getLogger(AggregationScheduler.class);

    private final AggregationService   aggregationService;
    private final SchedulerLockService lockService;

    public AggregationScheduler(AggregationService aggregationService,
                                  SchedulerLockService lockService) {
        this.aggregationService = aggregationService;
        this.lockService        = lockService;
    }

    /**
     * End-of-day aggregation: aggregate yesterday (full) + today (partial so far).
     * Runs at 00:30 daily.
     */
    @Scheduled(cron = "${switching.aggregation.daily-cron:0 30 0 * * *}")
    public void runDailyAggregation() {
        if (!lockService.acquire("AGGREGATION_DAILY", 30)) {
            log.debug("Daily aggregation lock not acquired — another replica running");
            return;
        }
        try {
            log.info("Daily aggregation started");
            aggregationService.aggregateYesterdayAndToday();
            log.info("Daily aggregation finished");
        } catch (Exception ex) {
            log.error("Daily aggregation failed: {}", ex.getMessage(), ex);
        } finally {
            lockService.release("AGGREGATION_DAILY");
        }
    }

    /**
     * Intraday (hourly) aggregation: keep today's summaries fresh for dashboards.
     * Runs at HH:05 every hour.
     */
    @Scheduled(cron = "${switching.aggregation.hourly-cron:0 5 * * * *}")
    public void runHourlyAggregation() {
        if (!lockService.acquire("AGGREGATION_HOURLY", 10)) {
            log.debug("Hourly aggregation lock not acquired — another replica running");
            return;
        }
        try {
            log.debug("Intraday aggregation started for date={}", LocalDate.now());
            aggregationService.aggregateForDate(LocalDate.now());
        } catch (Exception ex) {
            log.warn("Intraday aggregation failed: {}", ex.getMessage(), ex);
        } finally {
            lockService.release("AGGREGATION_HOURLY");
        }
    }
}
