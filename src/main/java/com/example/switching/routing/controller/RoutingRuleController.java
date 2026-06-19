package com.example.switching.routing.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.switching.routing.dto.CreateRoutingRuleRequest;
import com.example.switching.routing.dto.RoutingResolveResponse;
import com.example.switching.routing.dto.RoutingRuleListResponse;
import com.example.switching.routing.dto.RoutingRuleResponse;
import com.example.switching.routing.dto.UpdateRoutingRuleRequest;
import com.example.switching.routing.service.RoutingRuleManagementService;
import com.example.switching.routing.service.RoutingService;

@RestController
@RequestMapping("/api/routing-rules")
public class RoutingRuleController {

    private final RoutingService routingService;
    private final RoutingRuleManagementService routingRuleManagementService;

    public RoutingRuleController(RoutingService routingService,
                                 RoutingRuleManagementService routingRuleManagementService) {
        this.routingService = routingService;
        this.routingRuleManagementService = routingRuleManagementService;
    }

    // ── READ ──────────────────────────────────────────────────────────────────

    @GetMapping
    public RoutingRuleListResponse list() {
        return routingService.list();
    }

    @GetMapping("/resolve")
    public RoutingResolveResponse resolve(
            @RequestParam String sourceBank,
            @RequestParam String destinationBank,
            @RequestParam String messageType) {
        return routingService.resolve(sourceBank, destinationBank, messageType);
    }

    // ── CACHE ─────────────────────────────────────────────────────────────────

    @PostMapping("/cache/clear")
    public Map<String, Object> clearCache() {
        routingService.clearCache();
        return Map.of(
                "message", "Routing cache cleared",
                "cacheSize", routingService.cacheSize());
    }

    // ── WRITE ─────────────────────────────────────────────────────────────────

    @PostMapping
    public ResponseEntity<RoutingRuleResponse> create(
            @RequestBody CreateRoutingRuleRequest request) {
        RoutingRuleResponse response = routingRuleManagementService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/{routeCode}")
    public RoutingRuleResponse update(
            @PathVariable String routeCode,
            @RequestBody UpdateRoutingRuleRequest request) {
        return routingRuleManagementService.update(routeCode, request);
    }
}
