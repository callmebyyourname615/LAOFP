package com.example.switching.traffic.ratelimit;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;

@Service
public class ParticipantTokenBucketService {

    private static final String OVERFLOW_BUCKET = "overflow:shared";

    private final ParticipantRateLimitPolicyService policyService;
    private final int maxIdentities;
    private final ConcurrentHashMap<String, BucketHolder> buckets = new ConcurrentHashMap<>();

    public ParticipantTokenBucketService(
            ParticipantRateLimitPolicyService policyService,
            @Value("${switching.security.rate-limit.max-identities:100000}") int maxIdentities) {
        this.policyService = policyService;
        this.maxIdentities = Math.max(1, maxIdentities);
    }

    public RateLimitDecision consume(String identity) {
        String safeIdentity = identity == null || identity.isBlank() ? "remote:unknown" : identity;
        String bucketKey = bucketKey(safeIdentity);
        ParticipantRateLimitPolicySnapshot snapshot = policyService.snapshot();
        ParticipantQuota quota = snapshot.policy().quotaForIdentity(safeIdentity);
        BucketHolder holder = buckets.compute(bucketKey, (key, existing) -> {
            if (existing == null || !existing.matches(snapshot.revision(), quota)) {
                return new BucketHolder(snapshot.revision(), quota, buildBucket(quota));
            }
            return existing;
        });

        ConsumptionProbe probe = holder.bucket().tryConsumeAndReturnRemaining(1);
        long retryAfter = probe.isConsumed()
                ? 0
                : Math.max(1, TimeUnit.NANOSECONDS.toSeconds(
                        probe.getNanosToWaitForRefill() + 999_999_999L));
        return new RateLimitDecision(
                probe.isConsumed(),
                quota.capacity(),
                probe.getRemainingTokens(),
                retryAfter,
                snapshot.revision(),
                safeIdentity);
    }

    int bucketCount() {
        return buckets.size();
    }

    private String bucketKey(String identity) {
        if (buckets.containsKey(identity) || buckets.size() < maxIdentities) {
            return identity;
        }
        return OVERFLOW_BUCKET;
    }

    private static Bucket buildBucket(ParticipantQuota quota) {
        Bandwidth bandwidth = Bandwidth.builder()
                .capacity(quota.capacity())
                .refillGreedy(quota.refillTokens(), quota.refillPeriod())
                .build();
        return Bucket.builder().addLimit(bandwidth).build();
    }

    private record BucketHolder(String revision, ParticipantQuota quota, Bucket bucket) {
        boolean matches(String currentRevision, ParticipantQuota currentQuota) {
            return revision.equals(currentRevision) && quota.equals(currentQuota);
        }
    }
}
