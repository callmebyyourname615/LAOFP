package com.example.switching.traffic.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;

class ParticipantRateLimitPolicyServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void reloadsValidPolicyAndRetainsLastGoodPolicyAfterInvalidUpdate() throws Exception {
        Path policy = tempDir.resolve("policy.yaml");
        Files.writeString(policy, policyJson("v1", 5));
        ParticipantRateLimitPolicyService service =
                new ParticipantRateLimitPolicyService(new ObjectMapper(), policy, 100);
        service.initialize();

        String firstRevision = service.snapshot().revision();
        assertThat(service.snapshot().policy().quotaForIdentity("participant:BANK_A").capacity())
                .isEqualTo(5);

        Files.writeString(policy, "{\"version\":\"broken\",\"defaultQuota\":null}");
        assertThat(service.reloadNow()).isFalse();
        assertThat(service.snapshot().revision()).isEqualTo(firstRevision);
        assertThat(service.snapshot().policy().quotaForIdentity("participant:BANK_A").capacity())
                .isEqualTo(5);
    }

    @Test
    void usesDefaultQuotaForUnknownParticipant() throws Exception {
        Path policy = tempDir.resolve("policy.yaml");
        Files.writeString(policy, policyJson("v1", 7));
        ParticipantRateLimitPolicyService service =
                new ParticipantRateLimitPolicyService(new ObjectMapper(), policy, 100);
        service.initialize();

        assertThat(service.snapshot().policy().quotaForIdentity("participant:BANK_Z").capacity())
                .isEqualTo(3);
    }

    @Test
    void loadsImmutableClasspathFallbackWhenExternalPolicyIsMissing() {
        Path missingPolicy = tempDir.resolve("missing-policy.yaml");
        ParticipantRateLimitPolicyService service =
                new ParticipantRateLimitPolicyService(new ObjectMapper(), missingPolicy, 100);

        service.initialize();

        assertThat(service.snapshot().policy().version()).isEqualTo("phase70-v1");
        assertThat(service.snapshot().revision()).hasSize(64);
        assertThat(service.snapshot().policy().quotaForIdentity("participant:BANK_A").capacity())
                .isEqualTo(300);
    }

    private static String policyJson(String version, int bankACapacity) {
        return """
                {
                  "version":"%s",
                  "defaultQuota":{"capacity":3,"refillTokens":3,"refillPeriodSeconds":60},
                  "participants":{"BANK_A":{"capacity":%d,"refillTokens":%d,"refillPeriodSeconds":60}}
                }
                """.formatted(version, bankACapacity, bankACapacity);
    }
}
