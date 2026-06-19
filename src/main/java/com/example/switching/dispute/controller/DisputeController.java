package com.example.switching.dispute.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.switching.dispute.dto.DisputeRaiseRequest;
import com.example.switching.dispute.dto.DisputeRaiseResponse;
import com.example.switching.dispute.dto.DisputeRespondRequest;
import com.example.switching.dispute.dto.DisputeResolveRequest;
import com.example.switching.dispute.dto.DisputeResponse;
import com.example.switching.dispute.service.DisputeRaiseService;
import com.example.switching.dispute.service.DisputeResolutionService;

/**
 * Dispute & Refund API.
 *
 * <pre>
 * POST /v1/disputes/raise              — raise a new dispute         (BANK/ADMIN)
 * GET  /v1/disputes/{disputeId}        — dispute detail              (BANK/OPS/ADMIN)
 * PUT  /v1/disputes/{disputeId}/respond — responding PSP responds    (BANK/ADMIN)
 * POST /v1/disputes/{disputeId}/resolve — resolve dispute            (BANK/ADMIN)
 * GET  /v1/disputes?pspId=             — list disputes for a PSP     (BANK/OPS/ADMIN)
 * </pre>
 */
@RestController
@RequestMapping("/v1/disputes")
public class DisputeController {

    private final DisputeRaiseService      raiseService;
    private final DisputeResolutionService resolutionService;

    public DisputeController(DisputeRaiseService raiseService,
                             DisputeResolutionService resolutionService) {
        this.raiseService      = raiseService;
        this.resolutionService = resolutionService;
    }

    @PostMapping("/raise")
    public ResponseEntity<DisputeRaiseResponse> raise(
            @Valid @RequestBody DisputeRaiseRequest request) {
        return ResponseEntity.ok(raiseService.raise(request));
    }

    @GetMapping("/{disputeId}")
    public ResponseEntity<DisputeResponse> getDispute(@PathVariable Long disputeId) {
        return ResponseEntity.ok(resolutionService.getDispute(disputeId));
    }

    @PutMapping("/{disputeId}/respond")
    public ResponseEntity<DisputeResponse> respond(
            @PathVariable Long disputeId,
            @Valid @RequestBody DisputeRespondRequest request) {
        return ResponseEntity.ok(
                resolutionService.respond(disputeId, request.callingPspId(), request.evidence()));
    }

    @PostMapping("/{disputeId}/resolve")
    public ResponseEntity<DisputeResponse> resolve(
            @PathVariable Long disputeId,
            @Valid @RequestBody DisputeResolveRequest request) {
        return ResponseEntity.ok(
                resolutionService.resolve(disputeId, request.callingPspId(),
                        request.decision(), request.note(), false));
    }

    @GetMapping
    public ResponseEntity<List<DisputeResponse>> list(
            @RequestParam String pspId) {
        return ResponseEntity.ok(resolutionService.listForPsp(pspId));
    }
}
