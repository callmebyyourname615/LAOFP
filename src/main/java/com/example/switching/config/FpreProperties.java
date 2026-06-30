package com.example.switching.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * FPRE retry-schedule and auto-suspension configuration.
 *
 * <pre>
 * switching.fpre:
 *   retry-attempts: 4
 *   retry-delays-seconds: 1,1800,3600
 *   jitter-percent: 0
 *   auto-reversal-enabled: true
 *   suspension-window-minutes: 30
 *   suspension-reversal-threshold: 3
 * </pre>
 */
@Component
@ConfigurationProperties(prefix = "switching.fpre")
public class FpreProperties {

    /**
     * Total dispatch failure budget, including the initial forward failure.
     *
     * <p>Default 4 means: initial forward fails, then LAPNET/LMPS performs
     * exactly 3 push-forward retries before terminal failure.
     */
    private int retryAttempts = 4;

    /**
     * Base delay in seconds for retryable failures.
     *
     * <p>Default: first retry immediately after timeout, second retry 30 minutes later,
     * final retry 60 minutes after the previous retry.
     */
    private String retryDelaysSeconds = "1,1800,3600";

    /** Jitter percentage applied to each delay (±N%). Default: 0 for scheme fidelity. */
    private int jitterPercent = 0;

    /** Whether auto-reversal fires when all retries are exhausted. Default: true. */
    private boolean autoReversalEnabled = true;

    /** Rolling window (minutes) for counting reversals per PSP. Default: 30. */
    private int suspensionWindowMinutes = 30;

    /** Reversal count within the window that triggers PSP suspension. Default: 3. */
    private int suspensionReversalThreshold = 3;

    // ── Derived helpers ──────────────────────────────────────────────────────

    /** Parse retryDelaysSeconds into an int array. */
    public int[] parsedDelays() {
        String[] parts = retryDelaysSeconds.split(",");
        int[] delays = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            delays[i] = Integer.parseInt(parts[i].trim());
        }
        return delays;
    }

    // ── Getters / Setters ────────────────────────────────────────────────────

    public int getRetryAttempts() { return retryAttempts; }
    public void setRetryAttempts(int retryAttempts) { this.retryAttempts = retryAttempts; }

    public String getRetryDelaysSeconds() { return retryDelaysSeconds; }
    public void setRetryDelaysSeconds(String retryDelaysSeconds) { this.retryDelaysSeconds = retryDelaysSeconds; }

    public int getJitterPercent() { return jitterPercent; }
    public void setJitterPercent(int jitterPercent) { this.jitterPercent = jitterPercent; }

    public boolean isAutoReversalEnabled() { return autoReversalEnabled; }
    public void setAutoReversalEnabled(boolean autoReversalEnabled) { this.autoReversalEnabled = autoReversalEnabled; }

    public int getSuspensionWindowMinutes() { return suspensionWindowMinutes; }
    public void setSuspensionWindowMinutes(int suspensionWindowMinutes) { this.suspensionWindowMinutes = suspensionWindowMinutes; }

    public int getSuspensionReversalThreshold() { return suspensionReversalThreshold; }
    public void setSuspensionReversalThreshold(int suspensionReversalThreshold) { this.suspensionReversalThreshold = suspensionReversalThreshold; }
}
