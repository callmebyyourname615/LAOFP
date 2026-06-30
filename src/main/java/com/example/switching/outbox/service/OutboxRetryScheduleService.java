package com.example.switching.outbox.service;

import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.stereotype.Service;

import com.example.switching.config.FpreProperties;

/**
 * FPRE push-forward retry schedule with optional ±N% jitter.
 *
 * <p>The default policy follows the LAPNET/LMPS push-forward flow:
 * initial forward failure, then only 3 retry pushes:
 * <ol>
 *   <li>first retry immediately after timeout/non-definitive result,</li>
 *   <li>second retry 30 minutes after the first retry,</li>
 *   <li>last retry 60 minutes after the second retry.</li>
 * </ol>
 * Jitter can still be enabled by configuration when a deployment needs worker
 * de-synchronisation.
 *
 * <p>Default delays:
 * <pre>
 *   attempt 1 → 1 s
 *   attempt 2 → 1800 s
 *   attempt 3 → 3600 s
 * </pre>
 */
@Service
public class OutboxRetryScheduleService {

    private final FpreProperties fpre;

    public OutboxRetryScheduleService(FpreProperties fpre) {
        this.fpre = fpre;
    }

    /**
     * Compute the next retry timestamp for a given 1-based attempt number.
     *
     * @param attemptNo 1-based attempt number (1 = first retry after initial failure)
     * @return {@link LocalDateTime} when the event should next be processed
     */
    public LocalDateTime computeNextRetry(int attemptNo) {
        int[] delays = fpre.parsedDelays();
        int idx = Math.max(0, Math.min(attemptNo - 1, delays.length - 1));
        int baseSeconds = delays[idx];
        long jitteredSeconds = applyJitter(baseSeconds, fpre.getJitterPercent());
        return LocalDateTime.now().plusSeconds(jitteredSeconds);
    }

    /**
     * Returns true if {@code attemptNo} is still within the allowed retry budget.
     *
     * @param attemptNo 1-based attempt number (after this failure)
     */
    public boolean canRetry(int attemptNo) {
        return attemptNo < fpre.getRetryAttempts();
    }

    /**
     * Returns true when this attempt is the final one and no more retries remain.
     *
     * @param attemptNo 1-based attempt number (after this failure)
     */
    public boolean isFinalAttempt(int attemptNo) {
        return attemptNo >= fpre.getRetryAttempts();
    }

    // ── private ──────────────────────────────────────────────────────────────

    private long applyJitter(int baseSeconds, int jitterPercent) {
        if (jitterPercent <= 0) {
            return baseSeconds;
        }
        long jitterRange = Math.round(baseSeconds * (jitterPercent / 100.0));
        if (jitterRange == 0) {
            return baseSeconds;
        }
        long delta = ThreadLocalRandom.current().nextLong(-jitterRange, jitterRange + 1);
        return Math.max(1L, baseSeconds + delta);
    }
}
