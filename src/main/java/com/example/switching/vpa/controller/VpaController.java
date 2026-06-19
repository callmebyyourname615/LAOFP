package com.example.switching.vpa.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.switching.vpa.dto.VpaDetailResponse;
import com.example.switching.vpa.dto.VpaLookupRequest;
import com.example.switching.vpa.dto.VpaLookupResponse;
import com.example.switching.vpa.dto.VpaRegisterRequest;
import com.example.switching.vpa.dto.VpaUpdateRequest;
import com.example.switching.vpa.service.VpaLookupService;
import com.example.switching.vpa.service.VpaRegistrationService;

/**
 * VPA / Account Lookup REST endpoints.
 *
 * <pre>
 * POST   /v1/lookup/resolve          — resolve VPA → beneficiary token  (BANK/OPS/ADMIN)
 * POST   /v1/lookup/vpa/register     — register a new VPA               (BANK/ADMIN)
 * PUT    /v1/lookup/vpa/{vpaId}      — update accountRef / displayName   (BANK/ADMIN)
 * DELETE /v1/lookup/vpa/{vpaId}      — deregister (soft-delete)          (BANK/ADMIN)
 * GET    /v1/lookup/vpa/{vpaId}      — fetch VPA detail                  (BANK/OPS/ADMIN)
 * </pre>
 */
@RestController
@RequestMapping("${switching.api.v1-prefix}/lookup")
public class VpaController {

    private final VpaLookupService       lookupService;
    private final VpaRegistrationService registrationService;

    public VpaController(VpaLookupService lookupService,
                          VpaRegistrationService registrationService) {
        this.lookupService       = lookupService;
        this.registrationService = registrationService;
    }

    // ── Resolve ───────────────────────────────────────────────────────────────

    /**
     * Resolve a VPA address to a one-time beneficiary token.
     * The token expires after the configured TTL (default 5 minutes).
     */
    @PostMapping("/resolve")
    public ResponseEntity<VpaLookupResponse> resolve(
            @Validated @RequestBody VpaLookupRequest req) {
        return ResponseEntity.ok(lookupService.resolve(req));
    }

    // ── Registration management ───────────────────────────────────────────────

    /** Register a new VPA.  Returns 201 Created with the VPA detail. */
    @PostMapping("/vpa/register")
    public ResponseEntity<VpaDetailResponse> register(
            @Validated @RequestBody VpaRegisterRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(registrationService.register(req));
    }

    /** Update accountRef / displayName on an active VPA. */
    @PutMapping("/vpa/{vpaId}")
    public ResponseEntity<VpaDetailResponse> update(
            @PathVariable String vpaId,
            @Validated @RequestBody VpaUpdateRequest req) {
        return ResponseEntity.ok(registrationService.update(vpaId, req));
    }

    /** Soft-delete (deregister) a VPA. */
    @DeleteMapping("/vpa/{vpaId}")
    public ResponseEntity<Void> deregister(@PathVariable String vpaId) {
        registrationService.deregister(vpaId);
        return ResponseEntity.noContent().build();
    }

    /** Fetch full VPA detail by vpaId. */
    @GetMapping("/vpa/{vpaId}")
    public ResponseEntity<VpaDetailResponse> getById(@PathVariable String vpaId) {
        return ResponseEntity.ok(registrationService.getById(vpaId));
    }
}
