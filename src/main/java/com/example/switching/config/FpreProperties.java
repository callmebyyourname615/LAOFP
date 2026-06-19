package com.example.switching.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * FPRE retry-schedule and auto-suspension configuration.
 *
 * <pre>
 * switching.fpre:
 *   retry-attempts: 5
 *   retry-delays-seconds: 30,60,120,300,600
 *   jitter-percent: 10
 *   auto-reversal-enabled: true
 *   suspension-window-minutes: 30
 *   suspension-reversal-threshold: 3
 * </pre>
 */
@Component
@ConfigurationProperties(prefix = "switching.fpre")
public class FpreProperties {

    /** Total FPRE retry attempts (1–5). Default: 5. */
    private int retryAttempts = 5;

    /**
     * Base delay in seconds for each attempt (comma-separated, length must equal retryAttempts).
     * Default: 30,60,120,300,600.
     */
    private String retryDelaysSeconds = "30,60,120,300,600";

    /** Jitter percentage applied to each delay (±N%). Default: 10. */
    private int jitterPercent = 10;

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
