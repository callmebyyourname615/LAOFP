package com.example.switching.dashboard.transaction.controller;

import com.example.switching.dashboard.transaction.dto.TransactionDashboardResponse;
import com.example.switching.dashboard.transaction.service.TransactionDashboardService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard/transactions")
@ConditionalOnProperty(name = "switching.phase81.dashboards.enabled", havingValue = "true")
public class TransactionDashboardController {
    private final TransactionDashboardService service;
    public TransactionDashboardController(TransactionDashboardService service) { this.service = service; }

    @GetMapping("/summary")
    @PreAuthorize("hasAnyAuthority('PERM_DASHBOARD_TRANSACTION','PERM_DASHBOARD_SETTLEMENT')")
    public ResponseEntity<TransactionDashboardResponse> summary() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.load());
    }
}
