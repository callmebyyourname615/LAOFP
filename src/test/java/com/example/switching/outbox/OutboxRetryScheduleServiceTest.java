package com.example.switching.outbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import com.example.switching.config.FpreProperties;
import com.example.switching.outbox.service.OutboxRetryScheduleService;

/**
 * Unit tests for {@link OutboxRetryScheduleService} (TC-RETRY-001 – 009).
 *
 * Uses a real {@link FpreProperties} instance — no Spring context needed.
 *
 * Tests:
 * - computeNextRetry: 5 base delays (30,60,120,300,600 s) ± jitter
 * - computeNextRetry: beyond-max attempt clamps to last delay
 * - computeNextRetry: zero-jitter produces exact delay
 * - canRetry: true for attempts 1–4, false for attempt 5
 * - isFinalAttempt: true for attempt ≥ 5
 */
class OutboxRetryScheduleServiceTest {

    // Service under test — configured with 0% jitter for deterministic assertions
    private OutboxRetryScheduleService serviceNoJitter;

    // Service with default 10% jitter for range assertions
    private OutboxRetryScheduleService serviceWithJitter;

    @BeforeEach
    void setUp() {
        FpreProperties noJitter = new FpreProperties();
        noJitter.setRetryAttempts(5);
        noJitter.setRetryDelaysSeconds("30,60,120,300,600");
        noJitter.setJitterPercent(0);
        serviceNoJitter = new OutboxRetryScheduleService(noJitter);

        FpreProperties withJitter = new FpreProperties();
        withJitter.setRetryAttempts(5);
        withJitter.setRetryDelaysSeconds("30,60,120,300,600");
        withJitter.setJitterPercent(10);
        serviceWithJitter = new OutboxRetryScheduleService(withJitter);
    }

    // ── TC-RETRY-001 — attempt 1 → ~30 s ─────────────────────────────────────

    @Test
    void computeNextRetry_attempt1_isExactly30SecondsWithNoJitter() {
        LocalDateTime before = LocalDateTime.now();
        LocalDateTime nextRetry = serviceNoJitter.computeNextRetry(1);
        LocalDateTime after = LocalDateTime.now();

        long secondsAhead = ChronoUnit.SECONDS.between(before, nextRetry);
        assertTrue(secondsAhead >= 29 && secondsAhead <= 31,
                "Attempt 1 must be ~30 s ahead, got " + secondsAhead);
    }

    // ── TC-RETRY-002 — attempt 2 → ~60 s ─────────────────────────────────────

    @Test
    void computeNextRetry_attempt2_isExactly60SecondsWithNoJitter() {
        LocalDateTime before = LocalDateTime.now();
        LocalDateTime nextRetry = serviceNoJitter.computeNextRetry(2);

        long secondsAhead = ChronoUnit.SECONDS.between(before, nextRetry);
        assertTrue(secondsAhead >= 59 && secondsAhead <= 61,
                "Attempt 2 must be ~60 s ahead, got " + secondsAhead);
    }

    // ── TC-RETRY-003 — attempt 5 → ~600 s ────────────────────────────────────

    @Test
    void computeNextRetry_attempt5_isExactly600SecondsWithNoJitter() {
        LocalDateTime before = LocalDateTime.now();
        LocalDateTime nextRetry = serviceNoJitter.computeNextRetry(5);

        long secondsAhead = ChronoUnit.SECONDS.between(before, nextRetry);
        assertTrue(secondsAhead >= 599 && secondsAhead <= 601,
                "Attempt 5 must be ~600 s ahead, got " + secondsAhead);
    }

    // ── TC-RETRY-004 — attempt beyond max clamps to last delay ───────────────

    @Test
    void computeNextRetry_attemptBeyondMax_clampsToLastDelay() {
        LocalDateTime before = LocalDateTime.now();
        LocalDateTime nextRetry = serviceNoJitter.computeNextRetry(99);

        long secondsAhead = ChronoUnit.SECONDS.between(before, nextRetry);
        // Should use 600 s (the last configured delay)
        assertTrue(secondsAhead >= 599 && secondsAhead <= 601,
                "Attempt >5 must clamp to 600 s, got " + secondsAhead);
    }

    // ── TC-RETRY-005 — jitter: attempt 1 stays within ±10% of 30 s ──────────

    @RepeatedTest(20)
    void computeNextRetry_withJitter_attempt1_staysWithin10Percent() {
        LocalDateTime before = LocalDateTime.now();
        LocalDateTime nextRetry = serviceWithJitter.computeNextRetry(1);

        long secondsAhead = ChronoUnit.SECONDS.between(before, nextRetry);
        // ±10% of 30 s = [27, 33]
        assertTrue(secondsAhead >= 27 && secondsAhead <= 33,
                "Jittered attempt 1 must be in [27,33] s, got " + secondsAhead);
    }

    // ── TC-RETRY-006 — canRetry true for attempts 1–4 ────────────────────────

    @Test
    void canRetry_attempts1to4_returnsTrue() {
        assertTrue(serviceNoJitter.canRetry(1), "attempt 1 can retry");
        assertTrue(serviceNoJitter.canRetry(2), "attempt 2 can retry");
        assertTrue(serviceNoJitter.canRetry(3), "attempt 3 can retry");
        assertTrue(serviceNoJitter.canRetry(4), "attempt 4 can retry");
    }

    // ── TC-RETRY-007 — canRetry false for attempt 5 (max) ────────────────────

    @Test
    void canRetry_attempt5_returnsFalse() {
        assertFalse(serviceNoJitter.canRetry(5),
                "Attempt 5 (= retryAttempts) must NOT be retryable");
    }

    // ── TC-RETRY-008 — isFinalAttempt true for attempt ≥ 5 ──────────────────

    @Test
    void isFinalAttempt_attempt5AndAbove_returnsTrue() {
        assertTrue(serviceNoJitter.isFinalAttempt(5),  "attempt 5 is final");
        assertTrue(serviceNoJitter.isFinalAttempt(6),  "attempt 6 is also final (clamped)");
        assertTrue(serviceNoJitter.isFinalAttempt(99), "attempt 99 is also final");
    }

    // ── TC-RETRY-009 — isFinalAttempt false for attempt 4 ────────────────────

    @Test
    void isFinalAttempt_attempt4_returnsFalse() {
        assertFalse(serviceNoJitter.isFinalAttempt(4),
                "Attempt 4 is NOT the final attempt");
    }

    // ── TC-RETRY-010 — result is never null, always in the future ─────────────

    @Test
    void computeNextRetry_allAttempts_returnsNonNullFutureTimestamp() {
        LocalDateTime now = LocalDateTime.now();
        for (int i = 1; i <= 5; i++) {
            LocalDateTime result = serviceNoJitter.computeNextRetry(i);
            assertNotNull(result, "attempt " + i + " result must not be null");
            assertTrue(result.isAfter(now), "attempt " + i + " must be in the future");
        }
    }
}
