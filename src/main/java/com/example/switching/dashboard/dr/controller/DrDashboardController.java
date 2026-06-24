package com.example.switching.dashboard.dr.controller;

import com.example.switching.dashboard.dr.dto.DrDashboardResponse;
import com.example.switching.dashboard.dr.service.DrDashboardService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard/dr")
@ConditionalOnProperty(name = "switching.phase81.dashboards.enabled", havingValue = "true")
public class DrDashboardController {
  private final DrDashboardService service;
  public DrDashboardController(DrDashboardService service){this.service=service;}
  @GetMapping
  @PreAuthorize("hasAnyAuthority('PERM_DASHBOARD_DR','PERM_DASHBOARD_INFRASTRUCTURE')")
  public ResponseEntity<DrDashboardResponse> get(){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.load());}
}
