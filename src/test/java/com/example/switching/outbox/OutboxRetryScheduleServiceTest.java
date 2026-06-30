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
 * - computeNextRetry: 3 retry-push delays (1,1800,3600 s)
 * - computeNextRetry: beyond-max attempt clamps to last delay
 * - computeNextRetry: zero-jitter produces exact delay
 * - canRetry: true for attempts 1–3, false for attempt 4
 * - isFinalAttempt: true for attempt ≥ 4
 */
class OutboxRetryScheduleServiceTest {

    // Service under test — configured with 0% jitter for deterministic assertions
    private OutboxRetryScheduleService serviceNoJitter;

    // Service with 10% jitter for range assertions
    private OutboxRetryScheduleService serviceWithJitter;

    @BeforeEach
    void setUp() {
        FpreProperties noJitter = new FpreProperties();
        noJitter.setRetryAttempts(4);
        noJitter.setRetryDelaysSeconds("1,1800,3600");
        noJitter.setJitterPercent(0);
        serviceNoJitter = new OutboxRetryScheduleService(noJitter);

        FpreProperties withJitter = new FpreProperties();
        withJitter.setRetryAttempts(4);
        withJitter.setRetryDelaysSeconds("1,1800,3600");
        withJitter.setJitterPercent(10);
        serviceWithJitter = new OutboxRetryScheduleService(withJitter);
    }

    // ── TC-RETRY-001 — attempt 1 → ~1 s ──────────────────────────────────────

    @Test
    void computeNextRetry_attempt1_isExactly1SecondWithNoJitter() {
        LocalDateTime before = LocalDateTime.now();
        LocalDateTime nextRetry = serviceNoJitter.computeNextRetry(1);

        long secondsAhead = ChronoUnit.SECONDS.between(before, nextRetry);
        assertTrue(secondsAhead >= 0 && secondsAhead <= 2,
                "Attempt 1 must be ~1 s ahead, got " + secondsAhead);
    }

    // ── TC-RETRY-002 — attempt 2 → ~1800 s ───────────────────────────────────

    @Test
    void computeNextRetry_attempt2_isExactly1800SecondsWithNoJitter() {
        LocalDateTime before = LocalDateTime.now();
        LocalDateTime nextRetry = serviceNoJitter.computeNextRetry(2);

        long secondsAhead = ChronoUnit.SECONDS.between(before, nextRetry);
        assertTrue(secondsAhead >= 1799 && secondsAhead <= 1801,
                "Attempt 2 must be ~1800 s ahead, got " + secondsAhead);
    }

    // ── TC-RETRY-003 — attempt 3 → ~3600 s ───────────────────────────────────

    @Test
    void computeNextRetry_attempt3_isExactly3600SecondsWithNoJitter() {
        LocalDateTime before = LocalDateTime.now();
        LocalDateTime nextRetry = serviceNoJitter.computeNextRetry(3);

        long secondsAhead = ChronoUnit.SECONDS.between(before, nextRetry);
        assertTrue(secondsAhead >= 3599 && secondsAhead <= 3601,
                "Attempt 3 must be ~3600 s ahead, got " + secondsAhead);
    }

    // ── TC-RETRY-004 — attempt beyond max clamps to last delay ───────────────

    @Test
    void computeNextRetry_attemptBeyondMax_clampsToLastDelay() {
        LocalDateTime before = LocalDateTime.now();
        LocalDateTime nextRetry = serviceNoJitter.computeNextRetry(99);

        long secondsAhead = ChronoUnit.SECONDS.between(before, nextRetry);
        // Should use 3600 s (the last configured delay)
        assertTrue(secondsAhead >= 3599 && secondsAhead <= 3601,
                "Attempt >3 must clamp to 3600 s, got " + secondsAhead);
    }

    // ── TC-RETRY-005 — jitter: attempt 2 stays within ±10% of 1800 s ────────

    @RepeatedTest(20)
    void computeNextRetry_withJitter_attempt2_staysWithin10Percent() {
        LocalDateTime before = LocalDateTime.now();
        LocalDateTime nextRetry = serviceWithJitter.computeNextRetry(2);

        long secondsAhead = ChronoUnit.SECONDS.between(before, nextRetry);
        // ±10% of 1800 s = [1620, 1980]
        assertTrue(secondsAhead >= 1620 && secondsAhead <= 1980,
                "Jittered attempt 2 must be in [1620,1980] s, got " + secondsAhead);
    }

    // ── TC-RETRY-006 — canRetry true for attempts 1–3 ────────────────────────

    @Test
    void canRetry_attempts1to3_returnsTrue() {
        assertTrue(serviceNoJitter.canRetry(1), "attempt 1 can retry");
        assertTrue(serviceNoJitter.canRetry(2), "attempt 2 can retry");
        assertTrue(serviceNoJitter.canRetry(3), "attempt 3 can retry");
    }

    // ── TC-RETRY-007 — canRetry false for attempt 4 (max) ────────────────────

    @Test
    void canRetry_attempt4_returnsFalse() {
        assertFalse(serviceNoJitter.canRetry(4),
                "Attempt 4 (= retryAttempts) must NOT be retryable");
    }

    // ── TC-RETRY-008 — isFinalAttempt true for attempt ≥ 4 ──────────────────

    @Test
    void isFinalAttempt_attempt4AndAbove_returnsTrue() {
        assertTrue(serviceNoJitter.isFinalAttempt(4),  "attempt 4 is final");
        assertTrue(serviceNoJitter.isFinalAttempt(5),  "attempt 5 is also final (clamped)");
        assertTrue(serviceNoJitter.isFinalAttempt(99), "attempt 99 is also final");
    }

    // ── TC-RETRY-009 — isFinalAttempt false for attempt 3 ────────────────────

    @Test
    void isFinalAttempt_attempt3_returnsFalse() {
        assertFalse(serviceNoJitter.isFinalAttempt(3),
                "Attempt 3 is NOT the final attempt");
    }

    // ── TC-RETRY-010 — result is never null, always in the future ─────────────

    @Test
    void computeNextRetry_allAttempts_returnsNonNullFutureTimestamp() {
        LocalDateTime now = LocalDateTime.now();
        for (int i = 1; i <= 3; i++) {
            LocalDateTime result = serviceNoJitter.computeNextRetry(i);
            assertNotNull(result, "attempt " + i + " result must not be null");
            assertTrue(result.isAfter(now), "attempt " + i + " must be in the future");
        }
    }
}
