package com.example.switching.dashboard.settlement.controller;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.example.switching.dashboard.settlement.dto.SettlementDashboardResponse;
import com.example.switching.dashboard.settlement.service.SettlementDashboardService;

@RestController
@RequestMapping("/api/dashboard/settlement")
@ConditionalOnProperty(name = "switching.smos.enabled", havingValue = "true")
public class SettlementDashboardController {
    private final SettlementDashboardService dashboard;
    public SettlementDashboardController(SettlementDashboardService dashboard) { this.dashboard = dashboard; }

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_DASHBOARD_SETTLEMENT')")
    public ResponseEntity<SettlementDashboardResponse> get() {
        SettlementDashboardResponse response = dashboard.load();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Data-Freshness", response.generatedAt().toString()).body(response);
    }
}
