package com.example.switching.traffic.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;

class ParticipantTokenBucketServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void concurrentRequestsCannotExceedParticipantCapacity() throws Exception {
        ParticipantTokenBucketService buckets = serviceWithCapacity(10);
        CountDownLatch ready = new CountDownLatch(20);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(20)) {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int index = 0; index < 40; index++) {
                results.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return buckets.consume("participant:BANK_A").allowed();
                }));
            }
            ready.await();
            start.countDown();

            int accepted = 0;
            for (Future<Boolean> result : results) {
                if (result.get()) {
                    accepted++;
                }
            }
            assertThat(accepted).isEqualTo(10);
        }
    }

    @Test
    void policyRevisionRebuildsExistingParticipantBucket() throws Exception {
        Path policy = tempDir.resolve("reload-policy.yaml");
        Files.writeString(policy, policyJson("v1", 1));
        ParticipantRateLimitPolicyService policyService =
                new ParticipantRateLimitPolicyService(new ObjectMapper(), policy, 100);
        policyService.initialize();
        ParticipantTokenBucketService buckets = new ParticipantTokenBucketService(policyService, 100);

        assertThat(buckets.consume("participant:BANK_A").allowed()).isTrue();
        assertThat(buckets.consume("participant:BANK_A").allowed()).isFalse();

        Files.writeString(policy, policyJson("v2", 2));
        assertThat(policyService.reloadNow()).isTrue();
        assertThat(buckets.consume("participant:BANK_A").allowed()).isTrue();
        assertThat(buckets.consume("participant:BANK_A").allowed()).isTrue();
        assertThat(buckets.consume("participant:BANK_A").allowed()).isFalse();
    }

    @Test
    void boundsUntrustedIdentityBucketCardinality() throws Exception {
        ParticipantTokenBucketService buckets = serviceWithCapacity(10, 2);

        buckets.consume("remote:one");
        buckets.consume("remote:two");
        buckets.consume("remote:three");
        buckets.consume("remote:four");

        assertThat(buckets.bucketCount()).isLessThanOrEqualTo(3);
    }

    private ParticipantTokenBucketService serviceWithCapacity(int capacity) throws Exception {
        return serviceWithCapacity(capacity, 100);
    }

    private ParticipantTokenBucketService serviceWithCapacity(int capacity, int maxIdentities)
            throws Exception {
        Path policy = tempDir.resolve("policy-" + capacity + "-" + maxIdentities + ".yaml");
        Files.writeString(policy, policyJson("v1", capacity));
        ParticipantRateLimitPolicyService policyService =
                new ParticipantRateLimitPolicyService(new ObjectMapper(), policy, 100);
        policyService.initialize();
        return new ParticipantTokenBucketService(policyService, maxIdentities);
    }

    private static String policyJson(String version, int capacity) {
        return """
                {
                  "version":"%s",
                  "defaultQuota":{"capacity":%d,"refillTokens":%d,"refillPeriodSeconds":3600},
                  "participants":{"BANK_A":{"capacity":%d,"refillTokens":%d,"refillPeriodSeconds":3600}}
                }
                """.formatted(version, capacity, capacity, capacity, capacity);
    }
}
