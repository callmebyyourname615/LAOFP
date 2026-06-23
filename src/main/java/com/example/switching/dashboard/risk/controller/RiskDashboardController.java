package com.example.switching.dashboard.risk.controller;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.example.switching.dashboard.risk.dto.RiskDashboardResponse;
import com.example.switching.dashboard.risk.service.RiskDashboardService;

@RestController
@RequestMapping("/api/dashboard/risk")
@ConditionalOnProperty(name = "switching.smos.enabled", havingValue = "true")
public class RiskDashboardController {
    private final RiskDashboardService dashboard;
    public RiskDashboardController(RiskDashboardService dashboard) { this.dashboard = dashboard; }

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_DASHBOARD_RISK')")
    public ResponseEntity<RiskDashboardResponse> get() {
        RiskDashboardResponse response = dashboard.load();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Data-Freshness", response.generatedAt().toString()).body(response);
    }
}
