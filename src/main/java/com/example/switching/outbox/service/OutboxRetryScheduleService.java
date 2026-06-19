package com.example.switching.outbox.service;

import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.stereotype.Service;

import com.example.switching.config.FpreProperties;

/**
 * FPRE 5-attempt retry schedule with ±N% jitter.
 *
 * <p>Base delays (attempt 1–5): 30 s, 60 s, 120 s, 300 s, 600 s.
 * Jitter is applied as ±{jitterPercent}% of the base delay so that a cluster
 * of workers does not retry simultaneously.
 *
 * <p>Example with 10 % jitter:
 * <pre>
 *   attempt 1 → 27–33 s
 *   attempt 2 → 54–66 s
 *   attempt 3 → 108–132 s
 *   attempt 4 → 270–330 s
 *   attempt 5 → 540–660 s
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
