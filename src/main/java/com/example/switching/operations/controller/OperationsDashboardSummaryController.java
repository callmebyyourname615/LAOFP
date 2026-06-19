package com.example.switching.operations.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.switching.operations.dto.OperationsDashboardSummaryResponse;
import com.example.switching.operations.service.OperationsDashboardSummaryService;

@RestController
@RequestMapping("/api/operations")
public class OperationsDashboardSummaryController {

    private final OperationsDashboardSummaryService dashboardSummaryService;

    public OperationsDashboardSummaryController(
            OperationsDashboardSummaryService dashboardSummaryService
    ) {
        this.dashboardSummaryService = dashboardSummaryService;
    }

    @GetMapping("/dashboard-summary")
    public OperationsDashboardSummaryResponse getDashboardSummary() {
        return dashboardSummaryService.getDashboardSummary();
    }
}