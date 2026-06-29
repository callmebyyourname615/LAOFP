package com.example.switching.promotion.controller;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.switching.promotion.dto.CreatePromotionRequest;
import com.example.switching.promotion.dto.PromotionResponse;
import com.example.switching.promotion.service.PromotionManagementService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("${switching.api.v1-prefix:/v1}/promotions")
@ConditionalOnProperty(
        prefix = "switching.phase-ii.promotion",
        name = "enabled",
        havingValue = "true")
public class PromotionController {

    private final PromotionManagementService service;

    public PromotionController(PromotionManagementService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<PromotionResponse> create(
            @Valid @RequestBody CreatePromotionRequest request,
            Authentication authentication) {
        return ResponseEntity.status(201).body(service.create(
                request,
                actor(authentication)));
    }

    @PostMapping("/{id}/activate")
    public PromotionResponse activate(
            @PathVariable UUID id,
            Authentication authentication) {
        return service.activate(id, actor(authentication));
    }

    @PatchMapping("/{id}/suspend")
    public PromotionResponse suspend(
            @PathVariable UUID id,
            Authentication authentication) {
        return service.suspend(id, actor(authentication));
    }

    @PatchMapping("/{id}/extend")
    public PromotionResponse extend(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body,
            Authentication authentication) {
        String value = body.get("endsAt");
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("endsAt is required");
        }
        return service.extend(id, Instant.parse(value), actor(authentication));
    }

    @GetMapping("/{id}/report")
    public Map<String, Object> report(@PathVariable UUID id) {
        return service.report(id);
    }

    private static String actor(Authentication authentication) {
        return authentication == null ? "unknown" : authentication.getName();
    }
}
