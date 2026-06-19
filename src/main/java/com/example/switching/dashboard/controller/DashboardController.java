package com.example.switching.dashboard.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.switching.dashboard.dto.DashboardOverviewResponse;
import com.example.switching.dashboard.service.DashboardOverviewService;

/**
 * Operations dashboard API.
 *
 * <pre>
 * GET /api/dashboard/overview  — enriched overview (OPS / ADMIN)
 * </pre>
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardOverviewService dashboardOverviewService;

    public DashboardController(DashboardOverviewService dashboardOverviewService) {
        this.dashboardOverviewService = dashboardOverviewService;
    }

    /**
     * Returns the enriched dashboard overview:
     * today's volume + success rate, 24-h hourly trend, pool health, open disputes,
     * and the legacy per-status count lists.
     */
    @GetMapping("/overview")
    public ResponseEntity<DashboardOverviewResponse> getOverview() {
        return ResponseEntity.ok(dashboardOverviewService.getOverview());
    }
}
