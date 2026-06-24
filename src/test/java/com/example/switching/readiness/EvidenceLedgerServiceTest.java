package com.example.switching.readiness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.example.switching.readiness.dto.EvidenceInput;
import com.example.switching.readiness.model.ControlStatus;
import com.example.switching.readiness.service.EvidenceLedgerService;

class EvidenceLedgerServiceTest {
    @Test
    void appendsHashChainedEvidenceAndValidatesIntegrity() {
        EvidenceLedgerService ledger = new EvidenceLedgerService();
        var first = ledger.append(input("BUILD-MAVEN-VERIFY", "a".repeat(64)));
        var second = ledger.append(input("PERF-10K", "b".repeat(64)));
        assertEquals(first.recordHash(), second.previousHash());
        assertNotEquals(first.recordHash(), second.recordHash());
        assertTrue(ledger.validate().valid());
        assertEquals(2, ledger.validate().entryCount());
    }

    private static EvidenceInput input(String control, String artifactHash) {
        return new EvidenceInput("76", control, ControlStatus.PASS, "uat", "abc123", "sha256:image",
                Instant.parse("2026-06-23T00:00:00Z"), false, "QA", "evidence/" + control, artifactHash);
    }
}
