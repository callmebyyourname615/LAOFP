package com.example.switching.paymentorchestration;

import java.util.Map;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/operator/push-payment-policies")
@ConditionalOnProperty(
        prefix = "switching.phase-ii.push-payment-orchestrator",
        name = "enabled",
        havingValue = "true")
public class PushPaymentPolicyController {

    private final PolicyManagementService service;

    public PushPaymentPolicyController(PolicyManagementService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            @RequestBody Map<String, Object> body,
            Authentication authentication) {
        UUID id = service.createDraft(body, actor(authentication));
        return ResponseEntity.status(201).body(Map.of(
                "id", id,
                "status", "DRAFT"));
    }

    @PostMapping("/{id}/activate")
    public Map<String, Object> activate(
            @PathVariable UUID id,
            Authentication authentication) {
        service.activate(id, actor(authentication));
        return Map.of(
                "id", id,
                "status", "ACTIVE");
    }

    private static String actor(Authentication authentication) {
        return authentication == null ? "unknown" : authentication.getName();
    }
}
