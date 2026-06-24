package com.example.switching.traffic.ratelimit;

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.InputStream;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;

@Service
@Profile("!migration")
public class ParticipantRateLimitPolicyService {

    private static final Logger log = LoggerFactory.getLogger(ParticipantRateLimitPolicyService.class);

    private final ObjectMapper objectMapper;
    private final Path policyPath;
    private final int fallbackRequestsPerMinute;
    private final AtomicReference<ParticipantRateLimitPolicySnapshot> current = new AtomicReference<>();
    private final AtomicLong lastModifiedMillis = new AtomicLong(Long.MIN_VALUE);

    public ParticipantRateLimitPolicyService(
            ObjectMapper objectMapper,
            @Value("${switching.security.rate-limit.policy-file:config/phase70-participant-traffic-policy.yaml}")
                    String policyFile,
            @Value("${switching.security.rate-limit.requests-per-minute:100}")
                    int fallbackRequestsPerMinute) {
        this(objectMapper, Path.of(policyFile), fallbackRequestsPerMinute);
    }

    /** Required for CGLIB proxy ({@code @Scheduled} forces method-level interception). */
    protected ParticipantRateLimitPolicyService() {
        this.objectMapper = null;
        this.policyPath = null;
        this.fallbackRequestsPerMinute = 1;
    }

    ParticipantRateLimitPolicyService(
            ObjectMapper objectMapper,
            Path policyPath,
            int fallbackRequestsPerMinute) {
        this.objectMapper = objectMapper;
        this.policyPath = policyPath;
        this.fallbackRequestsPerMinute = Math.max(1, fallbackRequestsPerMinute);
    }

    @PostConstruct
    void initialize() {
        if (!reloadNow()) {
            ParticipantRateLimitPolicy fallback =
                    ParticipantRateLimitPolicy.fallback(fallbackRequestsPerMinute);
            current.compareAndSet(null, new ParticipantRateLimitPolicySnapshot(
                    "fallback", Instant.now(), fallback));
        }
    }

    @Scheduled(fixedDelayString = "${switching.security.rate-limit.policy-reload-interval:PT30S}")
    public void reloadIfChanged() {
        try {
            if (!Files.isRegularFile(policyPath)) {
                return;
            }
            long modified = Files.getLastModifiedTime(policyPath).toMillis();
            if (modified != lastModifiedMillis.get()) {
                reloadNow();
            }
        } catch (Exception exception) {
            log.error("Cannot inspect participant rate-limit policy path={}", policyPath, exception);
        }
    }

    public boolean reloadNow() {
        try {
            byte[] bytes;
            long modified;
            if (Files.isRegularFile(policyPath)) {
                bytes = Files.readAllBytes(policyPath);
                modified = Files.getLastModifiedTime(policyPath).toMillis();
            } else {
                try (InputStream input = ParticipantRateLimitPolicyService.class
                        .getResourceAsStream("/phase70/participant-traffic-policy.json")) {
                    if (input == null) {
                        log.warn("Participant rate-limit policy not found at {} and classpath fallback is absent",
                                policyPath);
                        return false;
                    }
                    bytes = input.readAllBytes();
                    modified = -1L;
                }
            }
            ParticipantRateLimitPolicy policy =
                    objectMapper.readValue(bytes, ParticipantRateLimitPolicy.class);
            String revision = sha256(bytes);
            current.set(new ParticipantRateLimitPolicySnapshot(revision, Instant.now(), policy));
            lastModifiedMillis.set(modified);
            log.info("Participant rate-limit policy loaded version={} revision={} overrides={}",
                    policy.version(), revision.substring(0, 12), policy.participants().size());
            return true;
        } catch (Exception exception) {
            log.error("Invalid participant rate-limit policy at {}; retaining last good policy",
                    policyPath, exception);
            return false;
        }
    }

    public ParticipantRateLimitPolicySnapshot snapshot() {
        ParticipantRateLimitPolicySnapshot snapshot = current.get();
        if (snapshot != null) {
            return snapshot;
        }
        ParticipantRateLimitPolicy fallback =
                ParticipantRateLimitPolicy.fallback(fallbackRequestsPerMinute);
        ParticipantRateLimitPolicySnapshot created =
                new ParticipantRateLimitPolicySnapshot("fallback", Instant.now(), fallback);
        current.compareAndSet(null, created);
        return current.get();
    }

    private static String sha256(byte[] value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    }
}
