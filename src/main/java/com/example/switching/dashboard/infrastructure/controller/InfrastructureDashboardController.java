package com.example.switching.dashboard.infrastructure.controller;

import com.example.switching.dashboard.infrastructure.dto.InfrastructureDashboardResponse;
import com.example.switching.dashboard.infrastructure.service.InfrastructureDashboardService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard/infrastructure")
@ConditionalOnProperty(name = "switching.phase81.dashboards.enabled", havingValue = "true")
public class InfrastructureDashboardController {
    private final InfrastructureDashboardService service;
    public InfrastructureDashboardController(InfrastructureDashboardService service){this.service=service;}
    @GetMapping
    @PreAuthorize("hasAnyAuthority('PERM_DASHBOARD_INFRASTRUCTURE','PERM_DASHBOARD_RISK')")
    public ResponseEntity<InfrastructureDashboardResponse> get(){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.load());
    }
}
