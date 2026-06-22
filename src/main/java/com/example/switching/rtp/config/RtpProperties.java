package com.example.switching.rtp.config;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "switching.phase-ii.rtp")
public class RtpProperties {

    private boolean enabled;
    private Duration defaultExpiry = Duration.ofHours(24);
    private Duration maximumExpiry = Duration.ofDays(30);
    private Map<String, Duration> participantExpiry = new HashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Duration getDefaultExpiry() {
        return defaultExpiry;
    }

    public void setDefaultExpiry(Duration defaultExpiry) {
        this.defaultExpiry = defaultExpiry;
    }

    public Duration getMaximumExpiry() {
        return maximumExpiry;
    }

    public void setMaximumExpiry(Duration maximumExpiry) {
        this.maximumExpiry = maximumExpiry;
    }

    public Map<String, Duration> getParticipantExpiry() {
        return participantExpiry;
    }

    public void setParticipantExpiry(Map<String, Duration> participantExpiry) {
        this.participantExpiry = participantExpiry == null ? new HashMap<>() : new HashMap<>(participantExpiry);
    }

    public Duration resolveExpiry(String participantId) {
        Duration configured = participantExpiry.getOrDefault(participantId, defaultExpiry);
        if (configured == null || configured.isZero() || configured.isNegative()) {
            throw new IllegalStateException("RTP expiry must be positive");
        }
        if (maximumExpiry == null || maximumExpiry.isZero() || maximumExpiry.isNegative()) {
            throw new IllegalStateException("RTP maximum expiry must be positive");
        }
        if (configured.compareTo(maximumExpiry) > 0) {
            throw new IllegalStateException("RTP participant expiry exceeds configured maximum");
        }
        return configured;
    }
}
