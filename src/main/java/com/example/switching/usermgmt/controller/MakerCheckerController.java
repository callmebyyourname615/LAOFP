package com.example.switching.usermgmt.controller;

import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.example.switching.usermgmt.dto.*;
import com.example.switching.usermgmt.service.MakerCheckerService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/requests")
@ConditionalOnProperty(name = "switching.smos.enabled", havingValue = "true")
public class MakerCheckerController {
    private final MakerCheckerService requests;
    public MakerCheckerController(MakerCheckerService requests) { this.requests = requests; }

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_MAKER_CHECKER_SUBMIT')")
    public ResponseEntity<MakerCheckerResponse> submit(@Valid @RequestBody MakerCheckerSubmitRequest request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(requests.submit(request.requestType(), request.payload(), authentication.getName()));
    }
    @GetMapping
    @PreAuthorize("hasAuthority('PERM_MAKER_CHECKER_APPROVE')")
    public ResponseEntity<List<MakerCheckerResponse>> pending() { return ResponseEntity.ok(requests.pending()); }
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('PERM_MAKER_CHECKER_APPROVE')")
    public ResponseEntity<MakerCheckerResponse> approve(@PathVariable UUID id,
            @Valid @RequestBody(required = false) MakerCheckerDecisionRequest decision, Authentication authentication) {
        return ResponseEntity.ok(requests.approve(id, authentication.getName(), decision == null ? null : decision.notes()));
    }
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('PERM_MAKER_CHECKER_APPROVE')")
    public ResponseEntity<MakerCheckerResponse> reject(@PathVariable UUID id,
            @Valid @RequestBody(required = false) MakerCheckerDecisionRequest decision, Authentication authentication) {
        return ResponseEntity.ok(requests.reject(id, authentication.getName(), decision == null ? null : decision.notes()));
    }
}
