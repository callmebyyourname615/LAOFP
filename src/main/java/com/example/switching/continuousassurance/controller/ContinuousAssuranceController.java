package com.example.switching.continuousassurance.controller;

import java.time.Instant;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.switching.continuousassurance.dto.HypercareEvent;
import com.example.switching.continuousassurance.dto.HypercareSummary;
import com.example.switching.continuousassurance.dto.ReadinessScorecard;
import com.example.switching.continuousassurance.dto.ReconciliationSnapshot;
import com.example.switching.continuousassurance.dto.SloSnapshot;
import com.example.switching.continuousassurance.service.ContinuousReadinessScoringService;
import com.example.switching.continuousassurance.service.HypercareService;

@RestController
@RequestMapping("/api/operations/continuous-assurance")
@ConditionalOnProperty(prefix = "switching.continuous-assurance", name = "enabled", havingValue = "true")
@PreAuthorize("hasAnyRole('ADMIN','OPS','SYSTEM_ADMIN')")
public class ContinuousAssuranceController {
    private final HypercareService hypercare;
    private final ContinuousReadinessScoringService scoring;

    public ContinuousAssuranceController(HypercareService hypercare, ContinuousReadinessScoringService scoring) {
        this.hypercare = hypercare;
        this.scoring = scoring;
    }

    @PostMapping("/hypercare/start") @PreAuthorize("hasAnyRole('ADMIN','SYSTEM_ADMIN')")
    public HypercareSummary start() { return hypercare.start(Instant.now()); }
    @PostMapping("/hypercare/events") public HypercareEvent event(@RequestBody HypercareEvent event) {
        return hypercare.addEvent(event.day(), event.type(), event.summary(), event.owner());
    }
    @GetMapping("/hypercare") public HypercareSummary hypercare() { return hypercare.summary(); }
    @PostMapping("/hypercare/complete") @PreAuthorize("hasAnyRole('ADMIN','SYSTEM_ADMIN')")
    public HypercareSummary complete() { return hypercare.complete(); }

    @PostMapping("/scorecard")
    public ReadinessScorecard score(@RequestBody ScoreRequest request) {
        return scoring.score(request.slo(), request.reconciliation(), request.backupHealth(),
                request.drReadiness(), request.secretFreshness(), request.alertHealth());
    }

    public record ScoreRequest(SloSnapshot slo, ReconciliationSnapshot reconciliation,
            double backupHealth, double drReadiness, double secretFreshness, double alertHealth) {}
}
