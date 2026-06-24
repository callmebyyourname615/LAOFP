package com.example.switching.readiness.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.example.switching.readiness.config.ReadinessProperties;
import com.example.switching.readiness.dto.ApprovalRecord;
import com.example.switching.readiness.dto.ControlResult;
import com.example.switching.readiness.dto.IncidentRecord;
import com.example.switching.readiness.dto.ReadinessSummary;
import com.example.switching.readiness.dto.RiskRecord;
import com.example.switching.readiness.model.ApprovalStatus;
import com.example.switching.readiness.model.ControlStatus;
import com.example.switching.readiness.model.IncidentSeverity;
import com.example.switching.readiness.model.ReadinessDecision;

@Service
@ConditionalOnProperty(prefix = "switching.readiness", name = "enabled", havingValue = "true")
public class ReadinessDecisionService {
    private final ReadinessProperties properties;
    private final EvidenceLedgerService ledger;
    private final Map<String, ControlResult> controls = new ConcurrentHashMap<>();
    private final Map<String, ApprovalRecord> approvals = new ConcurrentHashMap<>();
    private final Map<String, RiskRecord> risks = new ConcurrentHashMap<>();
    private final Map<String, IncidentRecord> incidents = new ConcurrentHashMap<>();

    public ReadinessDecisionService(ReadinessProperties properties, EvidenceLedgerService ledger) {
        this.properties = properties;
        this.ledger = ledger;
    }

    public void putControl(ControlResult result) { controls.put(result.controlId(), result); }
    public void putApproval(ApprovalRecord approval) { approvals.put(approval.role(), approval); }
    public void putRisk(RiskRecord risk) { risks.put(risk.riskId(), risk); }
    public void putIncident(IncidentRecord incident) { incidents.put(incident.incidentId(), incident); }
    public List<ControlResult> controls() { return controls.values().stream().sorted(Comparator.comparing(ControlResult::controlId)).toList(); }
    public List<ApprovalRecord> approvals() { return approvals.values().stream().sorted(Comparator.comparing(ApprovalRecord::role)).toList(); }
    public List<RiskRecord> risks() { return risks.values().stream().sorted(Comparator.comparing(RiskRecord::riskId)).toList(); }
    public List<IncidentRecord> incidents() { return incidents.values().stream().sorted(Comparator.comparing(IncidentRecord::incidentId)).toList(); }

    public ReadinessSummary evaluate(String releaseCommit, String imageDigest, boolean canaryRequested) {
        Instant now = Instant.now();
        List<String> blockers = new ArrayList<>();
        if (releaseCommit == null || releaseCommit.isBlank()) {
            return new ReadinessSummary(ReadinessDecision.BLOCKED, releaseCommit, imageDigest, 0, 0,
                    properties.getRequiredControls().size(), 0, 0, properties.getRequiredApprovalRoles().size(),
                    0, List.of("Release commit is required"), now);
        }
        Set<String> required = properties.getRequiredControls();
        int pass = 0, fail = 0, synthetic = 0, missing = 0;
        for (String controlId : required) {
            ControlResult control = controls.get(controlId);
            if (control == null || control.status() == ControlStatus.NOT_RUN) {
                missing++;
                blockers.add("Missing required control: " + controlId);
                continue;
            }
            if (control.status() == ControlStatus.PASS) pass++;
            else { fail++; blockers.add("Control not passing: " + controlId + "=" + control.status()); }
            if (control.synthetic()) { synthetic++; blockers.add("Synthetic evidence: " + controlId); }
            if (!releaseCommit.equals(control.gitCommit())) blockers.add("Commit mismatch: " + controlId);
            if (control.observedAt() == null || control.observedAt().plus(properties.getEvidenceMaxAge()).isBefore(now)) {
                blockers.add("Stale evidence: " + controlId);
            }
        }

        int validApprovals = 0;
        Set<String> uniqueApprovers = new HashSet<>();
        for (String role : properties.getRequiredApprovalRoles()) {
            ApprovalRecord approval = approvals.get(role);
            boolean valid = approval != null && approval.status() == ApprovalStatus.APPROVED
                    && releaseCommit.equals(approval.gitCommit())
                    && approval.expiresAt() != null && approval.expiresAt().isAfter(now)
                    && approval.evidenceTailHash() != null
                    && approval.evidenceTailHash().equals(ledger.validate().tailHash());
            if (valid) { validApprovals++; uniqueApprovers.add(approval.approver()); }
            else blockers.add("Missing or invalid approval: " + role);
        }

        if (validApprovals == properties.getRequiredApprovalRoles().size()
                && uniqueApprovers.size() < properties.getMinimumUniqueApprovers()) {
            blockers.add("Approval separation-of-duties failed: unique approvers=" + uniqueApprovers.size());
        }

        for (RiskRecord risk : risks.values()) {
            if (properties.getNonWaivableCategories().contains(risk.category())) {
                blockers.add("Non-waivable risk open: " + risk.riskId());
            } else if (!risk.waiverApproved() || risk.expiresAt() == null || !risk.expiresAt().isAfter(now)) {
                blockers.add("Unapproved/expired risk: " + risk.riskId());
            }
        }

        int criticalIncidents = (int) incidents.values().stream()
                .filter(i -> i.open() && i.severity() == IncidentSeverity.CRITICAL).count();
        if (criticalIncidents > 0) blockers.add("Open critical incidents: " + criticalIncidents);

        var ledgerValidation = ledger.validate();
        if (!ledgerValidation.valid()) blockers.add("Evidence ledger integrity failed");

        ReadinessDecision decision;
        if (criticalIncidents > 0) decision = ReadinessDecision.ROLLBACK;
        else if (controls.isEmpty() || missing > 0) decision = ReadinessDecision.PREPARED;
        else if (fail > 0 || synthetic > 0 || !ledgerValidation.valid()) decision = ReadinessDecision.NO_GO;
        else if (!blockers.isEmpty()) decision = ReadinessDecision.BLOCKED;
        else decision = canaryRequested ? ReadinessDecision.CANARY_GO : ReadinessDecision.GO;

        return new ReadinessSummary(decision, releaseCommit, imageDigest, pass, fail, missing, synthetic,
                validApprovals, properties.getRequiredApprovalRoles().size(), criticalIncidents,
                List.copyOf(blockers), now);
    }
}
