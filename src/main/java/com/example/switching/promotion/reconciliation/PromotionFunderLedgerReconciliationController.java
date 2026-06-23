package com.example.switching.promotion.reconciliation;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.switching.consistency.ReadConsistency;

@RestController
@RequestMapping("/api/operations/promotions/funder-ledger")
public class PromotionFunderLedgerReconciliationController {

    private final PromotionFunderLedgerReconciliationService service;

    public PromotionFunderLedgerReconciliationController(
            PromotionFunderLedgerReconciliationService service) {
        this.service = service;
    }

    @GetMapping("/reconciliation")
    public PromotionFunderLedgerReconciliationReport reconcile(
            @RequestParam(required = false) String funderParticipantId,
            @RequestParam(required = false) String currency,
            @RequestParam(defaultValue = "STRICT_PRIMARY") ReadConsistency consistency) {
        return service.reconcile(funderParticipantId, currency, consistency);
    }
}
