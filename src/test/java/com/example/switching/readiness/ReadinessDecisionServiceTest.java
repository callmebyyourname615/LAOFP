package com.example.switching.readiness;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.example.switching.readiness.config.ReadinessProperties;
import com.example.switching.readiness.dto.ApprovalRecord;
import com.example.switching.readiness.dto.ControlResult;
import com.example.switching.readiness.dto.EvidenceInput;
import com.example.switching.readiness.model.ApprovalStatus;
import com.example.switching.readiness.model.ControlStatus;
import com.example.switching.readiness.model.ReadinessDecision;
import com.example.switching.readiness.service.EvidenceLedgerService;
import com.example.switching.readiness.service.ReadinessDecisionService;

class ReadinessDecisionServiceTest {
    @Test
    void requiresCommitBoundApprovalsAndNonSyntheticControls() {
        ReadinessProperties properties = new ReadinessProperties();
        properties.setRequiredControls(Set.of("BUILD"));
        properties.setRequiredApprovalRoles(Set.of("QA_LEAD"));
        properties.setMinimumUniqueApprovers(1);
        EvidenceLedgerService ledger = new EvidenceLedgerService();
        ledger.append(new EvidenceInput("76", "BUILD", ControlStatus.PASS, "uat", "commit1", "image",
                Instant.now(), false, "qa", "build.json", "a".repeat(64)));
        ReadinessDecisionService service = new ReadinessDecisionService(properties, ledger);
        service.putControl(new ControlResult("BUILD", ControlStatus.PASS, true, false, "commit1",
                Instant.now(), Map.of(), Map.of(), "e1"));
        assertEquals(ReadinessDecision.BLOCKED, service.evaluate("commit1", "image", false).decision());
        service.putApproval(new ApprovalRecord("QA_LEAD", "qa@example", ApprovalStatus.APPROVED, "commit1",
                Instant.now(), Instant.now().plus(1, ChronoUnit.HOURS), ledger.validate().tailHash()));
        assertEquals(ReadinessDecision.GO, service.evaluate("commit1", "image", false).decision());
    }

    @Test
    void criticalControlFailureIsNoGo() {
        ReadinessProperties properties = new ReadinessProperties();
        properties.setRequiredControls(Set.of("FINANCIAL-INTEGRITY"));
        properties.setRequiredApprovalRoles(Set.of());
        EvidenceLedgerService ledger = new EvidenceLedgerService();
        ReadinessDecisionService service = new ReadinessDecisionService(properties, ledger);
        service.putControl(new ControlResult("FINANCIAL-INTEGRITY", ControlStatus.FAIL, true, false,
                "commit1", Instant.now(), Map.of("mismatch", 1), Map.of("mismatch", 0), "e1"));
        assertEquals(ReadinessDecision.NO_GO, service.evaluate("commit1", "image", false).decision());
    }
}
