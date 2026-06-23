package com.example.switching.dashboard.crossborder.controller;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.example.switching.dashboard.crossborder.dto.CrossBorderDashboardResponse;
import com.example.switching.dashboard.crossborder.service.CrossBorderDashboardService;

@RestController
@RequestMapping({"/api/dashboard/cross-border", "/api/dashboard/crossborder"})
@ConditionalOnProperty(name = "switching.smos.enabled", havingValue = "true")
public class CrossBorderDashboardController {
    private final CrossBorderDashboardService dashboard;
    public CrossBorderDashboardController(CrossBorderDashboardService dashboard) { this.dashboard = dashboard; }

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_DASHBOARD_CROSSBORDER')")
    public ResponseEntity<CrossBorderDashboardResponse> get() {
        CrossBorderDashboardResponse response = dashboard.load();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Data-Freshness", response.generatedAt().toString()).body(response);
    }
}
