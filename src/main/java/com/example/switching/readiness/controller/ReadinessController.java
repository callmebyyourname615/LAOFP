package com.example.switching.readiness.controller;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.switching.readiness.dto.ApprovalRecord;
import com.example.switching.readiness.dto.ControlResult;
import com.example.switching.readiness.dto.DecisionRequest;
import com.example.switching.readiness.dto.IncidentRecord;
import com.example.switching.readiness.dto.EvidenceInput;
import com.example.switching.readiness.dto.EvidenceEntry;
import com.example.switching.readiness.dto.LedgerValidation;
import com.example.switching.readiness.dto.ReadinessSummary;
import com.example.switching.readiness.dto.RiskRecord;
import com.example.switching.readiness.service.EvidenceLedgerService;
import com.example.switching.readiness.service.ReadinessDecisionService;

@RestController
@RequestMapping("/api/operations/readiness")
@ConditionalOnProperty(prefix = "switching.readiness", name = "enabled", havingValue = "true")
@PreAuthorize("hasAnyRole('ADMIN','OPS','SYSTEM_ADMIN')")
public class ReadinessController {
    private final ReadinessDecisionService decisions;
    private final EvidenceLedgerService ledger;

    public ReadinessController(ReadinessDecisionService decisions, EvidenceLedgerService ledger) {
        this.decisions = decisions;
        this.ledger = ledger;
    }

    @GetMapping("/controls") public List<ControlResult> controls() { return decisions.controls(); }
    @GetMapping("/approvals") public List<ApprovalRecord> approvals() { return decisions.approvals(); }
    @GetMapping("/risks") public List<RiskRecord> risks() { return decisions.risks(); }
    @GetMapping("/incidents") public List<IncidentRecord> incidents() { return decisions.incidents(); }
    @GetMapping("/evidence") public Object evidence() { return ledger.entries(); }
    @GetMapping("/evidence/integrity") public LedgerValidation integrity() { return ledger.validate(); }
    @PostMapping("/evidence") @PreAuthorize("hasAnyRole('ADMIN','SYSTEM_ADMIN')")
    public EvidenceEntry evidence(@RequestBody EvidenceInput input) { return ledger.append(input); }

    @PostMapping("/decisions")
    @PreAuthorize("hasAnyRole('ADMIN','SYSTEM_ADMIN')")
    public ReadinessSummary decide(@RequestBody DecisionRequest request) {
        return decisions.evaluate(request.releaseCommit(), request.imageDigest(), request.canaryRequested());
    }

    @PostMapping("/controls") public void control(@RequestBody ControlResult result) { decisions.putControl(result); }
    @PostMapping("/approvals") @PreAuthorize("hasAnyRole('ADMIN','SYSTEM_ADMIN')")
    public void approval(@RequestBody ApprovalRecord approval) { decisions.putApproval(approval); }
    @PostMapping("/risks") public void risk(@RequestBody RiskRecord risk) { decisions.putRisk(risk); }
    @PostMapping("/incidents") public void incident(@RequestBody IncidentRecord incident) { decisions.putIncident(incident); }
}
