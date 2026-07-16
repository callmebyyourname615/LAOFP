package com.example.switching.fees;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/operations/tariffs")
public class TariffOperationsController {

    private final TariffQueryService queryService;
    private final TariffManagementService managementService;
    private final TariffGovernanceService governanceService;
    private final FeeAssessmentService feeAssessmentService;

    public TariffOperationsController(
            TariffQueryService queryService,
            TariffManagementService managementService,
            TariffGovernanceService governanceService,
            FeeAssessmentService feeAssessmentService) {
        this.queryService = queryService;
        this.managementService = managementService;
        this.governanceService = governanceService;
        this.feeAssessmentService = feeAssessmentService;
    }

    @GetMapping
    public List<TariffPlanSummaryResponse> list() {
        return queryService.list();
    }

    @PostMapping
    public ResponseEntity<TariffPlanSummaryResponse> create(
            @RequestBody TariffManagementService.CreateTariffRequest request,
            Authentication authentication) {
        return ResponseEntity.status(201).body(managementService.create(request, actor(authentication)));
    }

    @PostMapping("/{versionId}/approve")
    public TariffPlanSummaryResponse approve(
            @PathVariable UUID versionId,
            @RequestBody Map<String, String> body,
            Authentication authentication) {
        governanceService.approve(versionId, actor(authentication), body.get("approvalReason"));
        return queryService.get(versionId);
    }

    @PostMapping("/{versionId}/activate")
    public TariffPlanSummaryResponse activate(@PathVariable UUID versionId) {
        governanceService.activate(versionId);
        return queryService.get(versionId);
    }

    @PostMapping("/assess")
    public FeeAssessmentResult assess(@RequestBody FeeAssessmentRequest request) {
        return feeAssessmentService.assess(
                request.transactionReference(),
                request.participantCode(),
                request.messageType(),
                request.currency(),
                request.amount());
    }

    private static String actor(Authentication authentication) {
        return authentication == null ? "unknown" : authentication.getName();
    }

    public record FeeAssessmentRequest(
            String transactionReference,
            String participantCode,
            String messageType,
            String currency,
            BigDecimal amount) {}
}
