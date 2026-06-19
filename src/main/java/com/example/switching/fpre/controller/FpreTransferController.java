package com.example.switching.fpre.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.switching.fpre.dto.FpreHealthResponse;
import com.example.switching.fpre.dto.FpreRetryHistoryResponse;
import com.example.switching.fpre.dto.FpreRetryStatusResponse;
import com.example.switching.fpre.dto.FpreTransferListResponse;
import com.example.switching.fpre.service.FpreOperationsService;

@RestController
@RequestMapping("${switching.api.v1-prefix}")
public class FpreTransferController {

    private final FpreOperationsService fpreOperationsService;

    public FpreTransferController(FpreOperationsService fpreOperationsService) {
        this.fpreOperationsService = fpreOperationsService;
    }

    @GetMapping("/transfers/{txnId}/retry-status")
    public ResponseEntity<FpreRetryStatusResponse> retryStatus(@PathVariable String txnId) {
        return ResponseEntity.ok(fpreOperationsService.retryStatus(txnId));
    }

    @GetMapping("/transfers/{txnId}/retry-history")
    public ResponseEntity<FpreRetryHistoryResponse> retryHistory(@PathVariable String txnId) {
        return ResponseEntity.ok(fpreOperationsService.retryHistory(txnId));
    }

    @GetMapping("/transfers/pending")
    public ResponseEntity<FpreTransferListResponse> pending(
            @RequestParam(value = "pspId", required = false) String pspId,
            @RequestParam(value = "limit", required = false) Integer limit,
            Authentication authentication) {
        return ResponseEntity.ok(fpreOperationsService.pending(resolvePspId(pspId, authentication), limit));
    }

    @GetMapping("/transfers/failed")
    public ResponseEntity<FpreTransferListResponse> failed(
            @RequestParam(value = "pspId", required = false) String pspId,
            @RequestParam(value = "limit", required = false) Integer limit,
            Authentication authentication) {
        return ResponseEntity.ok(fpreOperationsService.failed(resolvePspId(pspId, authentication), limit));
    }

    @GetMapping("/fpre/health")
    public ResponseEntity<FpreHealthResponse> health() {
        return ResponseEntity.ok(fpreOperationsService.health());
    }

    private String resolvePspId(String requestedPspId, Authentication authentication) {
        if (authentication == null || !isBank(authentication)) {
            return requestedPspId;
        }

        String principal = authentication.getName();
        if (principal == null || principal.isBlank()) {
            return requestedPspId;
        }
        return principal;
    }

    private boolean isBank(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_BANK"::equals);
    }
}
